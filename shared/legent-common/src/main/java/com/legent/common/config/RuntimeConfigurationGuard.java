package com.legent.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.legent.common.security.InternalApiTokenValidator;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RuntimeConfigurationGuard {

    private static final Set<String> PROTECTED_KEYS = Set.of(
            "legent.security.jwt.secret",
            "legent.tracking.signing-key",
            "legent.delivery.credential-key",
            "legent.delivery.encryption-salt",
            "legent.internal.api-token",
            "spring.datasource.password",
            "minio.secret-key"
    );

    private static final Set<String> UNSAFE_PRODUCTION_DDL_AUTO_VALUES = Set.of("update", "create", "create-drop");
    private static final String FRONTEND_BASE_URL_KEY = "legent.frontend.base-url";

    private final Environment environment;

    @PostConstruct
    public void validateProductionConfiguration() {
        if (!isProductionProfile()) {
            return;
        }

        for (String key : PROTECTED_KEYS) {
            String value = environment.getProperty(key);
            if (value != null && isPlaceholderSecret(value)) {
                throw new IllegalStateException("Production configuration contains placeholder value for " + key);
            }
        }

        boolean mockDns = environment.getProperty("legent.deliverability.mock-dns", Boolean.class, false);
        if (mockDns) {
            throw new IllegalStateException("Production configuration cannot enable legent.deliverability.mock-dns");
        }

        boolean allowMockProvider = environment.getProperty("legent.delivery.allow-mock-provider", Boolean.class, false);
        if (allowMockProvider) {
            throw new IllegalStateException("Production configuration cannot enable legent.delivery.allow-mock-provider");
        }

        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        if (ddlAuto != null && UNSAFE_PRODUCTION_DDL_AUTO_VALUES.contains(ddlAuto.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Production configuration cannot use unsafe spring.jpa.hibernate.ddl-auto=" + ddlAuto);
        }

        String frontendBaseUrl = environment.getProperty(FRONTEND_BASE_URL_KEY);
        if (frontendBaseUrl != null && isUnsafeProductionFrontendBaseUrl(frontendBaseUrl)) {
            throw new IllegalStateException("Production configuration cannot use unsafe " + FRONTEND_BASE_URL_KEY + "=" + frontendBaseUrl);
        }
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> "prod".equals(profile) || "production".equals(profile));
    }

    private boolean isPlaceholderSecret(String value) {
        return InternalApiTokenValidator.isPlaceholderLikeSecret(value);
    }

    private boolean isUnsafeProductionFrontendBaseUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return !"https".equals(scheme)
                    || "localhost".equals(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || "0.0.0.0".equals(host);
        } catch (IllegalArgumentException ex) {
            return true;
        }
    }
}
