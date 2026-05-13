package com.legent.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.legent.common.security.OutboundUrlGuard;
import com.legent.platform.domain.WebhookConfig;
import com.legent.platform.domain.WebhookLog;
import com.legent.platform.domain.WebhookRetry;
import com.legent.platform.repository.WebhookConfigRepository;
import com.legent.platform.repository.WebhookLogRepository;
import com.legent.platform.repository.WebhookRetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcherService {

    private final WebhookConfigRepository configRepository;
    private final WebhookLogRepository logRepository;
    private final WebhookRetryRepository retryRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Dispatches matching webhooks and only returns after delivery succeeds or a retry is durably stored.
     */
    public void dispatch(String tenantId, String eventType, Object payload) {
        
        List<WebhookConfig> configs = configRepository.findByTenantIdAndIsActiveTrue(tenantId);
        if (configs.isEmpty()) {
            return;
        }

        String normalizedEventType = normalize(eventType);
        if (normalizedEventType == null) {
            log.warn("Skipping webhook dispatch: event type is blank");
            return;
        }
        
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload", e);
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }

        // Collect all webhook dispatch Monos for concurrent execution
        List<Mono<Boolean>> dispatchMonos = new java.util.ArrayList<>();
        
        for (WebhookConfig config : configs) {
            
            // Check if this webhook is listening to this specific event
            if (!isSubscribed(config, normalizedEventType)) {
                continue;
            }

            URI endpoint;
            try {
                endpoint = OutboundUrlGuard.requirePublicHttpsUri(config.getEndpointUrl(), "webhook endpoint");
            } catch (IllegalArgumentException e) {
                log.warn("Skipping webhook {} due to unsafe endpoint URL: {}", config.getId(), e.getMessage());
                continue;
            }

            String signature = generateSignature(jsonPayload, config.getSecretKey());
            
            // AUDIT-014: Build reactive chain without blocking
            Mono<Boolean> dispatchMono = webClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Legent-Event", normalizedEventType)
                    .header("X-Legent-Signature", signature)
                    .bodyValue(jsonPayload)
                    .exchangeToMono(response -> {
                        return response.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
                            boolean isSuccess = response.statusCode().is2xxSuccessful();
                            logDelivery(tenantId, config.getId(), normalizedEventType, response.statusCode().value(), body, isSuccess);
                            return isSuccess;
                        });
                    })
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(300)).filter(this::isTransientWebhookError))
                    .onErrorResume(e -> {
                        logDelivery(tenantId, config.getId(), normalizedEventType, 0, e.getMessage(), false);
                        // AUDIT-015: Store failed webhook for retry
                        storeFailedWebhookForRetry(tenantId, config.getId(), normalizedEventType, jsonPayload, e.getMessage());
                        return Mono.just(false);
                    });
            
            dispatchMonos.add(dispatchMono);
        }
        
        // AUDIT-014: Wait for all dispatches to complete without blocking individual threads
        if (!dispatchMonos.isEmpty()) {
            Mono.when(dispatchMonos).block();
        }
    }
    
    /**
     * AUDIT-015: Store failed webhook for retry processing.
     * Persists failed webhook to database with exponential backoff schedule.
     */
    private void storeFailedWebhookForRetry(String tenantId, String webhookId, String eventType, String payload, String errorMessage) {
        try {
            WebhookRetry retry = new WebhookRetry();
            retry.setId(UUID.randomUUID().toString());
            retry.setTenantId(tenantId);
            retry.setWebhookId(webhookId);
            retry.setEventType(eventType);
            retry.setPayload(payload);
            retry.setErrorMessage(errorMessage);
            retry.setRetryCount(0);
            retry.setMaxRetries(3);
            retry.setStatus("PENDING");
            retry.setCreatedAt(Instant.now());
            retry.setNextRetryAt(Instant.now().plusSeconds(30)); // First retry in 30 seconds
            
            retryRepository.save(retry);
            log.info("Stored failed webhook for retry: id={}, tenant={}, webhook={}, event={}", 
                    retry.getId(), tenantId, webhookId, eventType);
        } catch (Exception e) {
            log.error("Failed to store webhook retry: tenant={}, webhook={}, error={}", 
                    tenantId, webhookId, e.getMessage(), e);
            throw new IllegalStateException("Failed to store webhook retry", e);
        }
    }

    /**
     * AUDIT-022: Generate HMAC signature for webhook payload.
     * Throws exception if secret is missing - webhooks must have secrets configured.
     */
    private String generateSignature(String payload, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Webhook secret is not configured. Cannot dispatch webhook without valid signature.");
        }
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate webhook signature", e);
        }
    }

    private void logDelivery(String tenantId, String webhookId, String eventType, int statusCode, String responseBody, boolean isSuccess) {
        WebhookLog whLog = new WebhookLog();
        whLog.setId(UUID.randomUUID().toString());
        whLog.setTenantId(tenantId);
        whLog.setWebhookId(webhookId);
        whLog.setEventType(eventType);
        whLog.setStatusCode(statusCode);
        whLog.setResponseBody(responseBody != null && responseBody.length() > 1000 ? responseBody.substring(0, 1000) : responseBody);
        whLog.setIsSuccess(isSuccess);
        logRepository.save(whLog);
    }

    private boolean isSubscribed(WebhookConfig config, String normalizedEventType) {
        String raw = normalize(config.getEventsSubscribed());
        if (raw == null) {
            return false;
        }
        return parseSubscribedEvents(raw).contains(normalizedEventType);
    }

    private Set<String> parseSubscribedEvents(String raw) {
        try {
            if (raw.startsWith("[")) {
                List<String> events = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
                return events.stream()
                        .map(this::normalize)
                        .filter(value -> value != null)
                        .collect(Collectors.toSet());
            }
        } catch (Exception e) {
            log.warn("Failed to parse webhook subscribed events '{}': {}", raw, e.getMessage());
            return Set.of();
        }

        return java.util.Arrays.stream(raw.split(","))
                .map(this::normalize)
                .filter(value -> value != null)
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    /**
     * AUDIT-014: Classify errors to determine if retry is appropriate.
     * Retry only on transient errors (5xx, timeouts, connection issues).
     * Do NOT retry on 4xx client errors (except 429 rate limit).
     */
    private boolean isTransientWebhookError(Throwable throwable) {
        // Never retry IllegalArgumentException (client errors)
        if (throwable instanceof IllegalArgumentException) {
            return false;
        }
        
        // Check for WebClientResponseException to examine HTTP status
        if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            org.springframework.web.reactive.function.client.WebClientResponseException wcre = 
                (org.springframework.web.reactive.function.client.WebClientResponseException) throwable;
            int status = wcre.getStatusCode().value();
            // Don't retry client errors (4xx) except 429 (Too Many Requests)
            if (status >= 400 && status < 500 && status != 429) {
                return false;
            }
        }
        
        // Retry everything else (5xx server errors, IO exceptions, timeouts, etc.)
        return true;
    }
}
