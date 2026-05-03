package com.legent.delivery.client;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
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
    private final Map<String, Map<String, String>> contentCache = new ConcurrentHashMap<>();

    public ContentServiceClient(
            @Value("${legent.content-service.url:http://content-service:8090}") String baseUrl,
            @Value("${legent.content-service.timeout-seconds:10}") int timeoutSeconds) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .option(ChannelOption.SO_KEEPALIVE, true);

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        log.info("ContentServiceClient initialized with baseUrl: {} and timeout: {}s", baseUrl, timeoutSeconds);
    }

    /**
     * Fetches campaign content from content-service with connection pooling.
     *
     * @param campaignId the campaign ID
     * @return Map containing subject, htmlBody, textBody (may be empty if not found)
     */
    public Map<String, String> fetchCampaignContent(String campaignId) {
        // Check in-memory cache first
        if (contentCache.containsKey(campaignId)) {
            log.debug("Content cache hit for campaign {}", campaignId);
            return contentCache.get(campaignId);
        }

        try {
            Map<String, String> result = webClient.get()
                    .uri("/api/v1/content/campaign/{campaignId}", campaignId)
                    .header("Accept", "application/json")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        log.warn("Content-service returned 4xx for campaign {}: {}", campaignId, response.statusCode());
                        return null;
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, response -> {
                        log.error("Content-service returned 5xx for campaign {}: {}", campaignId, response.statusCode());
                        return null;
                    })
                    .bodyToMono(ContentResponse.class)
                    .map(this::extractContent)
                    .onErrorReturn(Map.of())
                    .block(Duration.ofSeconds(10));

            // Cache successful results briefly (5 minutes) to reduce load
            if (!result.isEmpty()) {
                contentCache.put(campaignId, result);
                // Schedule cache eviction
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                        .schedule(() -> contentCache.remove(campaignId), 5, java.util.concurrent.TimeUnit.MINUTES);
            }

            return result;
        } catch (Exception e) {
            log.error("Error fetching content from content-service for campaign {}: {}", campaignId, e.getMessage());
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
        }
        return result;
    }

    // Record classes for JSON parsing
    private record ContentResponse(ContentData data) {}
    private record ContentData(String subject, String htmlBody, String textBody) {}
}
