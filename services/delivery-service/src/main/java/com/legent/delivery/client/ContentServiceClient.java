package com.legent.delivery.client;

import com.legent.common.security.InternalApiTokenValidator;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LEGENT-CRIT-003: Content Service Client with WebClient connection pooling.
 * Replaces HttpURLConnection to prevent port exhaustion under high load.
 */
@Slf4j
@Component
public class ContentServiceClient {

    private final WebClient webClient;
    private final Duration cacheTtl;
    private final Duration requestTimeout;
    private final String internalApiToken;
    private final int maxCacheEntries;
    private final Map<String, CacheEntry> contentCache = new ConcurrentHashMap<>();

    public ContentServiceClient(
            @Value("${legent.content-service.url:http://content-service:8090}") String baseUrl,
            @Value("${legent.content-service.timeout-seconds:10}") int timeoutSeconds,
            @Value("${legent.content-service.cache-ttl-seconds:300}") long cacheTtlSeconds,
            @Value("${legent.internal.api-token}") String internalApiToken) {
        this(baseUrl, timeoutSeconds, cacheTtlSeconds, internalApiToken, 1000);
    }

    @Autowired
    public ContentServiceClient(
            @Value("${legent.content-service.url:http://content-service:8090}") String baseUrl,
            @Value("${legent.content-service.timeout-seconds:10}") int timeoutSeconds,
            @Value("${legent.content-service.cache-ttl-seconds:300}") long cacheTtlSeconds,
            @Value("${legent.internal.api-token}") String internalApiToken,
            @Value("${legent.content-service.max-cache-entries:1000}") int maxCacheEntries) {
        validateInternalApiToken(internalApiToken);
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        this.internalApiToken = internalApiToken;
        this.maxCacheEntries = Math.max(0, maxCacheEntries);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(requestTimeout)
                .option(ChannelOption.SO_KEEPALIVE, true);

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        log.info("ContentServiceClient initialized with baseUrl: {}, timeout: {}s, cacheTtl: {}s, maxCacheEntries: {}",
                baseUrl, timeoutSeconds, cacheTtlSeconds, this.maxCacheEntries);
    }

    /**
     * Fetches rendered content by tenant/workspace-scoped reference from content-service.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param referenceId the rendered content reference ID
     * @return Map containing subject, htmlBody, textBody (may be empty if not found)
     */
    public Map<String, String> fetchRenderedContent(String tenantId, String workspaceId, String referenceId) {
        String scopedTenantId = normalize(tenantId);
        String scopedWorkspaceId = normalize(workspaceId);
        String scopedReferenceId = normalize(referenceId);
        if (scopedTenantId == null || scopedWorkspaceId == null || scopedReferenceId == null) {
            return Map.of();
        }
        String cacheKey = scopedTenantId + ":" + scopedWorkspaceId + ":" + scopedReferenceId;
        CacheEntry cached = contentCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Content cache hit for rendered reference {}", scopedReferenceId);
            return cached.content();
        }
        if (cached != null) {
            contentCache.remove(cacheKey, cached);
        }

        try {
            Map<String, String> result = webClient.get()
                    .uri("/api/v1/content/rendered-content/{referenceId}/internal", scopedReferenceId)
                    .header("Accept", "application/json")
                    .header("X-Internal-Token", internalApiToken)
                    .header("X-Tenant-Id", scopedTenantId)
                    .header("X-Workspace-Id", scopedWorkspaceId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        log.warn("Content-service returned 4xx for rendered reference {}: {}",
                                scopedReferenceId,
                                response.statusCode());
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalStateException(
                                        "Content-service returned " + response.statusCode()
                                                + " for rendered reference " + scopedReferenceId));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, response -> {
                        log.error("Content-service returned 5xx for rendered reference {}: {}",
                                scopedReferenceId,
                                response.statusCode());
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalStateException(
                                        "Content-service returned " + response.statusCode()
                                                + " for rendered reference " + scopedReferenceId));
                    })
                    .bodyToMono(ContentResponse.class)
                    .map(this::extractContent)
                    .onErrorReturn(Map.of())
                    .block(requestTimeout);

            if (!result.isEmpty() && maxCacheEntries > 0 && !cacheTtl.isZero() && !cacheTtl.isNegative()) {
                putCached(cacheKey, result);
            }

            return result;
        } catch (Exception e) {
            log.error("Error fetching content from content-service for rendered reference {}: {}",
                    scopedReferenceId,
                    e.getMessage());
            return Map.of();
        }
    }

    private Map<String, String> extractContent(ContentResponse response) {
        Map<String, String> result = new java.util.HashMap<>();
        if (response != null && response.data() != null) {
            ContentData data = response.data();
            if (data.subject() != null) {
                result.put("subject", data.subject());
            }
            if (data.htmlBody() != null) {
                result.put("htmlBody", data.htmlBody());
            }
            if (data.textBody() != null) {
                result.put("textBody", data.textBody());
            }
            if (data.tenantId() != null) {
                result.put("tenantId", data.tenantId());
            }
            if (data.workspaceId() != null) {
                result.put("workspaceId", data.workspaceId());
            }
            if (data.campaignId() != null) {
                result.put("campaignId", data.campaignId());
            }
            if (data.jobId() != null) {
                result.put("jobId", data.jobId());
            }
            if (data.batchId() != null) {
                result.put("batchId", data.batchId());
            }
            if (data.messageId() != null) {
                result.put("messageId", data.messageId());
            }
            if (data.contentId() != null) {
                result.put("contentId", data.contentId());
            }
            if (data.referenceId() != null) {
                result.put("referenceId", data.referenceId());
            }
            if (data.metadata() != null) {
                data.metadata().forEach((key, value) -> {
                    if (value != null) {
                        result.put(key, value);
                    }
                });
            }
        }
        return result;
    }

    private void validateInternalApiToken(String token) {
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", token);
    }

    private void putCached(String cacheKey, Map<String, String> result) {
        evictExpiredEntries();
        while (contentCache.size() >= maxCacheEntries) {
            String oldestKey = contentCache.entrySet().stream()
                    .min(Map.Entry.comparingByValue((left, right) -> left.expiresAt().compareTo(right.expiresAt())))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldestKey == null) {
                break;
            }
            contentCache.remove(oldestKey);
        }
        contentCache.put(cacheKey, new CacheEntry(result, Instant.now().plus(cacheTtl)));
    }

    private void evictExpiredEntries() {
        contentCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    // Record classes for JSON parsing
    private record CacheEntry(Map<String, String> content, Instant expiresAt) {
        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
    private record ContentResponse(ContentData data) {}
    private record ContentData(String tenantId,
                               String workspaceId,
                               String campaignId,
                               String jobId,
                               String batchId,
                               String messageId,
                               String contentId,
                               String referenceId,
                               String subject,
                               String htmlBody,
                               String textBody,
                               Map<String, String> metadata) {}
}
