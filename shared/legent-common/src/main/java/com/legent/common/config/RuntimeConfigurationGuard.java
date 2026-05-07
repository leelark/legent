package com.legent.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

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
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch("prod"::equals);
    }

    private boolean isPlaceholderSecret(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || normalized.contains("change_me")
                || normalized.contains("changeme")
                || normalized.contains("replace_in_production")
                || normalized.contains("dev-token")
                || normalized.equals("password")
                || normalized.equals("minioadmin");
    }
}
