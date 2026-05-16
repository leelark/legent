package com.legent.platform.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import com.legent.common.security.OutboundUrlGuard;
import com.legent.platform.domain.WebhookConfig;
import com.legent.platform.domain.WebhookLog;
import com.legent.platform.domain.WebhookRetry;
import com.legent.platform.repository.WebhookConfigRepository;
import com.legent.platform.repository.WebhookLogRepository;
import com.legent.platform.repository.WebhookRetryRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.Base64;

/**
 * AUDIT-015: Scheduled service for processing failed webhook retries.
 * Retries pending webhooks with exponential backoff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookRetryService {

    private final WebhookRetryRepository retryRepository;
    private final WebhookConfigRepository configRepository;
    private final WebhookLogRepository logRepository;
    private final WebClient webClient;
    @Qualifier("webhookRetryExecutor")
    private final Executor webhookRetryExecutor;

    private static final int MAX_RETRIES_PER_BATCH = 50;

    // Exponential backoff delays: 30s, 2min, 10min
    private static final Duration[] RETRY_DELAYS = {
        Duration.ofSeconds(30),
        Duration.ofMinutes(2),
        Duration.ofMinutes(10)
    };

    /**
     * LEGENT-CRIT-005: Process pending webhook retries every 30 seconds with parallelism.
     * Uses a bounded query and configured retry executor to prevent queue buildup.
     */
    @Scheduled(fixedDelay = 30000)
    public void processRetries() {
        Instant now = Instant.now();

        // Process PENDING retries with parallel processing
        List<WebhookRetry> pendingRetries = retryRepository.findPendingRetries(
                now, PageRequest.of(0, MAX_RETRIES_PER_BATCH));
        if (!pendingRetries.isEmpty()) {
            log.info("Processing {} pending webhook retries (max {} per batch)",
                    pendingRetries.size(), MAX_RETRIES_PER_BATCH);

            // Claim rows before async handoff so concurrent nodes do not process the same retry.
            List<CompletableFuture<Void>> futures = pendingRetries.stream()
                    .filter(retry -> claimPendingRetry(retry, now))
                    .map(retry -> CompletableFuture.runAsync(() -> processRetryAsync(retry), webhookRetryExecutor))
                    .toList();

            // Wait for all to complete (with timeout)
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .orTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                        .join();
            } catch (CompletionException ex) {
                log.warn("Some webhook retries timed out or failed: {}", ex.getMessage());
            }
        }
    }

    private boolean claimPendingRetry(WebhookRetry retry, Instant now) {
        Instant claimedAt = Instant.now();
        // This is a row claim, not an expiring lease; stale RETRYING recovery still needs a reconciler.
        int claimed = retryRepository.claimPendingRetry(retry.getId(), now, claimedAt);
        if (claimed == 0) {
            log.debug("Skipping webhook retry {} because another worker claimed it", retry.getId());
            return false;
        }

        retry.setStatus("RETRYING");
        retry.setUpdatedAt(claimedAt);
        return true;
    }

    /**
     * Async wrapper for processing a single retry.
     */
    private void processRetryAsync(WebhookRetry retry) {
        // Set tenant context for this async execution
        TenantContext.setTenantId(retry.getTenantId());
        try {
            processRetry(retry);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void processRetry(WebhookRetry retry) {
        try {
            // Mark as retrying
            retry.setStatus("RETRYING");
            retry.setUpdatedAt(Instant.now());
            retryRepository.save(retry);

            // Get webhook config within the retry tenant to avoid cross-tenant delivery.
            WebhookConfig config = configRepository.findByIdAndTenantId(retry.getWebhookId(), retry.getTenantId()).orElse(null);
            if (config == null || !Boolean.TRUE.equals(config.getIsActive())) {
                log.warn("Webhook config {} not found or inactive, marking retry as FAILED", retry.getWebhookId());
                markRetryFailed(retry, "Webhook configuration not found or inactive");
                return;
            }

            URI endpoint;
            try {
                endpoint = OutboundUrlGuard.requirePublicHttpsUri(config.getEndpointUrl(), "webhook endpoint");
            } catch (IllegalArgumentException e) {
                markRetryFailed(retry, e.getMessage());
                return;
            }

            // Generate signature
            String signature;
            try {
                signature = generateSignature(retry.getPayload(), config.getSecretKey());
            } catch (Exception e) {
                markRetryFailed(retry, "Failed to generate signature: " + e.getMessage());
                return;
            }

            // Attempt delivery
            boolean success = attemptDelivery(retry, config, endpoint, signature);
            
            if (success) {
                markRetrySuccess(retry);
            } else {
                scheduleNextRetryOrFail(retry, "HTTP error or timeout");
            }

        } catch (Exception e) {
            log.error("Error processing webhook retry {}", retry.getId(), e);
            scheduleNextRetryOrFail(retry, e.getMessage());
        }
    }

    private boolean attemptDelivery(WebhookRetry retry, WebhookConfig config, URI endpoint, String signature) {
        try {
            Mono<Boolean> dispatchMono = webClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Legent-Event", retry.getEventType())
                    .header("X-Legent-Signature", signature)
                    .header("X-Legent-Retry", String.valueOf(retry.getRetryCount() + 1))
                    .bodyValue(retry.getPayload())
                    .exchangeToMono(response -> {
                        return response.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
                            boolean isSuccess = response.statusCode().is2xxSuccessful();
                            // Log the retry attempt
                            logDelivery(retry.getTenantId(), retry.getWebhookId(), retry.getEventType(), 
                                    response.statusCode().value(), body, isSuccess);
                            return isSuccess;
                        });
                    })
                    .timeout(Duration.ofSeconds(10));

            return Boolean.TRUE.equals(dispatchMono.block());
        } catch (Exception e) {
            logDelivery(retry.getTenantId(), retry.getWebhookId(), retry.getEventType(), 0, e.getMessage(), false);
            return false;
        }
    }

    private void scheduleNextRetryOrFail(WebhookRetry retry, String errorMessage) {
        int nextRetryCount = retry.getRetryCount() + 1;
        
        if (nextRetryCount >= retry.getMaxRetries()) {
            markRetryFailed(retry, "Max retries exceeded. Last error: " + errorMessage);
        } else {
            // Schedule next retry with exponential backoff
            Duration delay = RETRY_DELAYS[Math.min(nextRetryCount - 1, RETRY_DELAYS.length - 1)];
            Instant nextRetryAt = Instant.now().plus(delay);
            
            retry.setRetryCount(nextRetryCount);
            retry.setStatus("PENDING");
            retry.setNextRetryAt(nextRetryAt);
            retry.setLastError(errorMessage);
            retry.setUpdatedAt(Instant.now());
            retryRepository.save(retry);
            
            log.info("Scheduled webhook retry {} for {} (attempt {}/{})"
                    , retry.getId(), nextRetryAt, nextRetryCount, retry.getMaxRetries());
        }
    }

    private void markRetrySuccess(WebhookRetry retry) {
        retry.setStatus("SUCCESS");
        retry.setUpdatedAt(Instant.now());
        retry.setLastError(null);
        retryRepository.save(retry);
        log.info("Webhook retry {} succeeded after {} attempts", retry.getId(), retry.getRetryCount() + 1);
    }

    private void markRetryFailed(WebhookRetry retry, String errorMessage) {
        retry.setStatus("FAILED");
        retry.setUpdatedAt(Instant.now());
        retry.setLastError(errorMessage);
        retryRepository.save(retry);
        log.warn("Webhook retry {} failed permanently: {}", retry.getId(), errorMessage);
    }

    private void logDelivery(String tenantId, String webhookId, String eventType, int statusCode, String responseBody, boolean isSuccess) {
        try {
            WebhookLog whLog = new WebhookLog();
            whLog.setId(UUID.randomUUID().toString());
            whLog.setTenantId(tenantId);
            whLog.setWebhookId(webhookId);
            whLog.setEventType(eventType);
            whLog.setStatusCode(statusCode);
            whLog.setResponseBody(responseBody != null && responseBody.length() > 1000 ? responseBody.substring(0, 1000) : responseBody);
            whLog.setIsSuccess(isSuccess);
            logRepository.save(whLog);
        } catch (Exception e) {
            log.error("Failed to log webhook delivery", e);
        }
    }

    private String generateSignature(String payload, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Webhook secret is not configured");
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
}
