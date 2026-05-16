package com.legent.campaign.client;

import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
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

@Component
public class DeliveryReadinessClient {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String baseUrl;
    private final String serviceBearerToken;

    public DeliveryReadinessClient(
            @Value("${legent.delivery-service.url:}") String baseUrl,
            @Value("${legent.service-auth.bearer-token:}") String serviceBearerToken) {
        this.baseUrl = trimToNull(baseUrl);
        this.serviceBearerToken = trimToNull(serviceBearerToken);
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

    public WarmupStatus warmupStatus(String tenantId, String workspaceId) {
        requireWebClient("delivery-service URL is not configured");
        requireBearerToken();
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/v1/delivery/warmup/status")
                    .headers(headers -> tenantHeaders(headers, tenantId, workspaceId))
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(READ_TIMEOUT)
                    .block();
            Map<String, Object> data = mapData(response, "warmup status");
            List<WarmupStateSnapshot> states = listValue(data.get("states")).stream()
                    .filter(Map.class::isInstance)
                    .map(item -> warmupState(castMap(item)))
                    .toList();
            return new WarmupStatus(
                    longValue(data.get("activeProviders")),
                    longValue(data.get("healthyProviders")),
                    longValue(data.get("degradedProviders")),
                    longValue(data.get("unhealthyProviders")),
                    stringValue(data.get("rampStage")),
                    intValue(data.get("allowedVolume")),
                    stringValue(data.get("rollbackReason")),
                    instantValue(data.get("nextIncreaseTime")),
                    states
            );
        } catch (WebClientResponseException e) {
            throw new ReadinessDependencyException("warmup status endpoint returned " + e.getStatusCode(), e);
        } catch (ReadinessDependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new ReadinessDependencyException("warmup status endpoint call failed", e);
        }
    }

    public ProviderCapacityDecision evaluateProviderCapacity(String tenantId,
                                                             String workspaceId,
                                                             String providerId,
                                                             String senderDomain,
                                                             Integer riskScore) {
        requireWebClient("delivery-service URL is not configured");
        requireBearerToken();
        try {
            Map<String, Object> response = webClient.post()
                    .uri("/api/v1/delivery/provider-capacity/evaluate")
                    .headers(headers -> tenantHeaders(headers, tenantId, workspaceId))
                    .bodyValue(Map.of(
                            "providerId", providerId,
                            "senderDomain", senderDomain,
                            "riskScore", riskScore == null ? 0 : riskScore
                    ))
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(READ_TIMEOUT)
                    .block();
            Map<String, Object> data = mapData(response, "provider capacity decision");
            return new ProviderCapacityDecision(
                    stringValue(data.get("providerId")),
                    upperValue(data.get("throttleState")),
                    intValue(data.get("recommendedPerMinute")),
                    intValue(data.get("recommendedPerSecond")),
                    stringValue(data.get("failoverProviderId")),
                    stringValue(data.get("reason")),
                    instantValue(data.get("evaluatedAt"))
            );
        } catch (WebClientResponseException e) {
            throw new ReadinessDependencyException("provider capacity endpoint returned " + e.getStatusCode(), e);
        } catch (ReadinessDependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new ReadinessDependencyException("provider capacity endpoint call failed", e);
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
        headers.setBearerAuth(serviceBearerToken);
    }

    private void requireWebClient(String message) {
        if (webClient == null || baseUrl == null) {
            throw new ReadinessDependencyException(message);
        }
    }

    private void requireBearerToken() {
        if (serviceBearerToken == null) {
            throw new ReadinessDependencyException("service bearer token is not configured for delivery readiness checks");
        }
    }

    private Map<String, Object> mapData(Map<String, Object> response, String label) {
        Object data = data(response, label);
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            map.forEach((key, value) -> result.put(Objects.toString(key), value));
            return result;
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

    private WarmupStateSnapshot warmupState(Map<String, Object> row) {
        return new WarmupStateSnapshot(
                normalize(row.get("senderDomain")),
                normalize(row.get("providerId")),
                upperValue(row.get("stage")),
                intValue(row.get("hourlyLimit")),
                intValue(row.get("dailyLimit")),
                intValue(row.get("sentThisHour")),
                intValue(row.get("sentToday")),
                doubleValue(row.get("bounceRate")),
                doubleValue(row.get("complaintRate")),
                stringValue(row.get("rollbackReason")),
                instantValue(row.get("nextIncreaseAt"))
        );
    }

    private List<Object> listValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalize(Object value) {
        String text = stringValue(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }

    private String upperValue(Object value) {
        String text = stringValue(value);
        return text == null ? null : text.trim().toUpperCase(Locale.ROOT);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ignored) {
            return 0;
        }
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
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception ignored) {
            return 0;
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

    public record WarmupStatus(
            long activeProviders,
            long healthyProviders,
            long degradedProviders,
            long unhealthyProviders,
            String rampStage,
            int allowedVolume,
            String rollbackReason,
            Instant nextIncreaseTime,
            List<WarmupStateSnapshot> states
    ) {}

    public record WarmupStateSnapshot(
            String senderDomain,
            String providerId,
            String stage,
            int hourlyLimit,
            int dailyLimit,
            int sentThisHour,
            int sentToday,
            double bounceRate,
            double complaintRate,
            String rollbackReason,
            Instant nextIncreaseAt
    ) {}

    public record ProviderCapacityDecision(
            String providerId,
            String throttleState,
            int recommendedPerMinute,
            int recommendedPerSecond,
            String failoverProviderId,
            String reason,
            Instant evaluatedAt
    ) {}
}
