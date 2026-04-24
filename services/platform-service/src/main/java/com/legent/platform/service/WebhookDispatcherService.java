package com.legent.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.legent.platform.domain.WebhookConfig;
import com.legent.platform.domain.WebhookLog;
import com.legent.platform.repository.WebhookConfigRepository;
import com.legent.platform.repository.WebhookLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcherService {

    private final WebhookConfigRepository configRepository;
    private final WebhookLogRepository logRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    
    @Async("webhookExecutor")
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
            return;
        }

        for (WebhookConfig config : configs) {
            
            // Check if this webhook is listening to this specific event
            if (!isSubscribed(config, normalizedEventType)) {
                continue;
            }

            if (normalize(config.getEndpointUrl()) == null) {
                log.warn("Skipping webhook {} due to missing endpoint URL", config.getId());
                continue;
            }

            String signature = generateSignature(jsonPayload, config.getSecretKey());

            webClient.post()
                    .uri(config.getEndpointUrl())
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
                        return Mono.just(false);
                    })
                    .block();
        }
    }

    private String generateSignature(String payload, String secret) {
        if (secret == null || secret.isEmpty()) return "";
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.warn("Failed to generate webhook signature", e);
            return "";
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

    private boolean isTransientWebhookError(Throwable throwable) {
        return !(throwable instanceof IllegalArgumentException);
    }
}
