package com.legent.campaign.client;

import com.legent.common.constant.AppConstants;
import com.legent.common.security.InternalServiceIdentity;
import com.legent.common.security.InternalApiTokenValidator;
import com.legent.security.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Slf4j
@Component
public class AudienceResolutionClient {

    private static final String SERVICE_NAME = "campaign-service";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String internalApiToken;

    public AudienceResolutionClient(
            @Value("${legent.audience-service.url:http://audience-service:8082}") String baseUrl,
            @Value("${legent.internal.api-token}") String internalApiToken) {
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(READ_TIMEOUT)
                .compress(true);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.internalApiToken = internalApiToken;
    }

    public ResolvedAudienceChunk readChunk(String tenantId, String workspaceId, String jobId, String chunkId) {
        String scopedTenantId = requireText("tenantId", tenantId);
        String scopedWorkspaceId = requireText("workspaceId", workspaceId);
        String scopedJobId = requireText("jobId", jobId);
        String scopedChunkId = requireText("chunkId", chunkId);
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/audience-resolution-chunks/{chunkId}/internal")
                            .queryParam("jobId", scopedJobId)
                            .build(scopedChunkId))
                    .headers(headers -> scopedHeaders(headers, scopedTenantId, scopedWorkspaceId, scopedJobId, scopedChunkId))
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(READ_TIMEOUT)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                            .doAfterRetry(sig -> log.warn("Retrying audience chunk read for {}/{}",
                                    scopedJobId,
                                    scopedChunkId)))
                    .block();
            Map<String, Object> data = responseData(response, scopedChunkId);
            return new ResolvedAudienceChunk(
                    requireResponseText(data, "jobId"),
                    requireResponseText(data, "chunkId"),
                    intValue(data.get("chunkIndex")),
                    intValue(data.get("chunkSize")),
                    booleanValue(data.get("isLastChunk")),
                    subscribers(data.get("subscribers")));
        } catch (AudienceResolutionClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AudienceResolutionClientException("Failed to read audience resolution chunk " + scopedChunkId, e);
        }
    }

    private Map<String, Object> responseData(Map<String, Object> response, String chunkId) {
        if (response == null || !(response.get("data") instanceof Map<?, ?> raw)) {
            throw new AudienceResolutionClientException("Invalid response from audience-service for chunk " + chunkId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        raw.forEach((key, value) -> data.put(String.valueOf(key), value));
        return data;
    }

    private List<Map<String, String>> subscribers(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawSubscribers)) {
            throw new AudienceResolutionClientException("Audience-service returned non-list subscribers");
        }
        return rawSubscribers.stream()
                .map(raw -> {
                    if (!(raw instanceof Map<?, ?> rawMap)) {
                        throw new AudienceResolutionClientException("Audience-service returned invalid subscriber payload");
                    }
                    Map<String, String> subscriber = new LinkedHashMap<>();
                    rawMap.forEach((key, entryValue) -> {
                        if (key != null && entryValue != null) {
                            subscriber.put(String.valueOf(key), String.valueOf(entryValue));
                        }
                    });
                    return Map.copyOf(subscriber);
                })
                .toList();
    }

    private void scopedHeaders(HttpHeaders headers, String tenantId, String workspaceId, String jobId, String chunkId) {
        headers.set(AppConstants.HEADER_TENANT_ID, tenantId);
        headers.set(AppConstants.HEADER_WORKSPACE_ID, workspaceId);
        setOptionalHeader(headers, AppConstants.HEADER_ENVIRONMENT_ID, TenantContext.getEnvironmentId());
        setOptionalHeader(headers, AppConstants.HEADER_REQUEST_ID, TenantContext.getRequestId());
        setOptionalHeader(headers, AppConstants.HEADER_CORRELATION_ID, TenantContext.getCorrelationId());
        headers.set("X-Internal-Token", internalApiToken);
        Instant timestamp = Instant.now();
        String action = chunkReadAction(jobId, chunkId);
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

    public static String chunkReadAction(String jobId, String chunkId) {
        return InternalServiceIdentity.scopedAction(
                InternalServiceIdentity.ACTION_AUDIENCE_RESOLUTION_CHUNK_READ,
                jobId,
                chunkId);
    }

    private void setOptionalHeader(HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    private String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new AudienceResolutionClientException(field + " is required for audience resolution chunk reads");
        }
        return value.trim();
    }

    private String requireResponseText(Map<String, Object> data, String field) {
        String value = data.get(field) == null ? null : String.valueOf(data.get(field)).trim();
        if (value == null || value.isBlank()) {
            throw new AudienceResolutionClientException("Audience-service returned incomplete chunk metadata: " + field);
        }
        return value;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    public record ResolvedAudienceChunk(String jobId,
                                        String chunkId,
                                        int chunkIndex,
                                        int chunkSize,
                                        boolean isLastChunk,
                                        List<Map<String, String>> subscribers) {
    }

    public static class AudienceResolutionClientException extends RuntimeException {
        public AudienceResolutionClientException(String message) {
            super(message);
        }

        public AudienceResolutionClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
