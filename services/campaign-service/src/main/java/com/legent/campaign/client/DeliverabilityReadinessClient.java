package com.legent.campaign.client;

import com.legent.common.constant.AppConstants;
import com.legent.common.security.InternalApiTokenValidator;
import com.legent.common.security.InternalServiceIdentity;
import com.legent.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class DeliverabilityReadinessClient {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};
    private static final String SERVICE_NAME = "campaign-service";

    private final WebClient webClient;
    private final String baseUrl;
    private final ServiceAuthTokenProvider serviceAuthTokenProvider;
    private final String internalApiToken;

    public DeliverabilityReadinessClient(
            @Value("${legent.deliverability-service.url:}") String baseUrl,
            ServiceAuthTokenProvider serviceAuthTokenProvider,
            @Value("${legent.internal.api-token:}") String internalApiToken) {
        this.baseUrl = trimToNull(baseUrl);
        this.serviceAuthTokenProvider = serviceAuthTokenProvider;
        this.internalApiToken = trimToNull(internalApiToken);
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(READ_TIMEOUT)
                .compress(true);
        this.webClient = this.baseUrl == null
                ? null
                : WebClient.builder()
                        .baseUrl(this.baseUrl)
                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build();
    }

    public List<AuthCheck> authChecks(String tenantId, String workspaceId) {
        requireWebClient("deliverability-service URL is not configured");
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/v1/deliverability/auth/checks")
                    .headers(headers -> tenantHeaders(headers, tenantId, workspaceId))
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(READ_TIMEOUT)
                    .block();
            return listData(response, "deliverability authentication checks").stream()
                    .filter(Map.class::isInstance)
                    .map(item -> authCheck(castMap(item)))
                    .toList();
        } catch (WebClientResponseException e) {
            throw new ReadinessDependencyException("deliverability authentication endpoint returned " + e.getStatusCode(), e);
        } catch (ReadinessDependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new ReadinessDependencyException("deliverability authentication endpoint call failed", e);
        }
    }

    public SuppressionHealth suppressionHealth(String tenantId, String workspaceId) {
        requireWebClient("deliverability-service URL is not configured");
        requireInternalApiToken("suppression health checks");
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/v1/deliverability/suppressions/internal/history")
                    .headers(headers -> {
                        tenantHeaders(headers, tenantId, workspaceId);
                        headers.set("X-Internal-Token", internalApiToken);
                        signInternal(headers, tenantId, workspaceId,
                                InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_HISTORY_READ);
                    })
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(READ_TIMEOUT)
                    .block();
            Map<String, Object> history = mapData(response, "suppression health");
            return new SuppressionHealth(
                    longValue(history.get("total")),
                    longValue(history.get("complaints")),
                    longValue(history.get("hardBounces")),
                    longValue(history.get("unsubscribes")),
                    instantValue(history.get("generatedAt")));
        } catch (WebClientResponseException e) {
            throw new ReadinessDependencyException("suppression health endpoint returned " + e.getStatusCode(), e);
        } catch (ReadinessDependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new ReadinessDependencyException("suppression health endpoint call failed", e);
        }
    }

    private void tenantHeaders(HttpHeaders headers, String tenantId, String workspaceId) {
        headers.set(AppConstants.HEADER_TENANT_ID, tenantId);
        headers.set(AppConstants.HEADER_WORKSPACE_ID, workspaceId);
        String environmentId = TenantContext.getEnvironmentId();
        if (environmentId != null && !environmentId.isBlank()) {
            headers.set(AppConstants.HEADER_ENVIRONMENT_ID, environmentId);
        }
        String requestId = TenantContext.getRequestId();
        if (requestId != null && !requestId.isBlank()) {
            headers.set(AppConstants.HEADER_REQUEST_ID, requestId);
        }
        headers.setBearerAuth(serviceAuthTokenProvider.tokenFor(tenantId, workspaceId));
    }

    private void signInternal(HttpHeaders headers, String tenantId, String workspaceId, String action) {
        Instant timestamp = Instant.now();
        headers.set(InternalServiceIdentity.HEADER_SERVICE, SERVICE_NAME);
        headers.set(InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, timestamp.toString());
        headers.set(InternalServiceIdentity.HEADER_SIGNATURE, InternalServiceIdentity.sign(
                internalApiToken,
                SERVICE_NAME,
                tenantId,
                workspaceId,
                action,
                timestamp));
    }

    private void requireWebClient(String message) {
        if (webClient == null || baseUrl == null) {
            throw new ReadinessDependencyException(message);
        }
    }

    private void requireInternalApiToken(String purpose) {
        try {
            InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
        } catch (IllegalStateException e) {
            throw new ReadinessDependencyException("internal API token is not configured for " + purpose);
        }
    }

    private List<Object> listData(Map<String, Object> response, String label) {
        Object data = data(response, label);
        if (data instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        throw new ReadinessDependencyException("invalid " + label + " response: data is not a list");
    }

    private Map<String, Object> mapData(Map<String, Object> response, String label) {
        Object data = data(response, label);
        if (data instanceof Map<?, ?> map) {
            return castMap(map);
        }
        throw new ReadinessDependencyException("invalid " + label + " response: data is not an object");
    }

    private Object data(Map<String, Object> response, String label) {
        if (response == null) {
            throw new ReadinessDependencyException("empty " + label + " response");
        }
        Object success = response.get("success");
        if (Boolean.FALSE.equals(success)) {
            throw new ReadinessDependencyException(label + " response was not successful");
        }
        Object data = response.get("data");
        if (data == null) {
            throw new ReadinessDependencyException("invalid " + label + " response: missing data");
        }
        return data;
    }

    private AuthCheck authCheck(Map<String, Object> row) {
        return new AuthCheck(
                stringValue(row.get("domainId")),
                normalizeDomain(stringValue(row.get("domain"))),
                upperValue(row.get("status")),
                booleanValue(row.get("spf")),
                booleanValue(row.get("dkim")),
                booleanValue(row.get("dmarc")),
                booleanValue(row.get("reverseDns")),
                instantValue(row.get("lastVerifiedAt"))
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeDomain(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String stringValue(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }

    private String upperValue(Object value) {
        String text = stringValue(value);
        return text == null ? null : text.trim().toUpperCase(Locale.ROOT);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            throw new ReadinessDependencyException("invalid suppression health response: count is not numeric", ex);
        }
    }

    private Instant instantValue(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    public record AuthCheck(
            String domainId,
            String domain,
            String status,
            boolean spf,
            boolean dkim,
            boolean dmarc,
            boolean reverseDns,
            Instant lastVerifiedAt
    ) {}

    public record SuppressionHealth(
            long total,
            long complaints,
            long hardBounces,
            long unsubscribes,
            Instant generatedAt
    ) {}
}
