package com.legent.foundation.service;

import com.legent.foundation.domain.WebhookIntegration;
import com.legent.foundation.repository.WebhookIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookIntegrationRepository repo;
    private final RestTemplate restTemplate;

    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);

    public void sendEvent(String eventType, String payload) {
        if (eventType == null || eventType.isBlank()) {
            log.warn("Skipping webhook dispatch because eventType is blank");
            return;
        }

        for (WebhookIntegration wh : repo.findAll()) {
            if (wh == null || wh.getEventType() == null || wh.getUrl() == null || wh.getUrl().isBlank()) {
                continue;
            }

            if (Objects.equals(wh.getEventType(), eventType)) {
                dispatchWithRetry(wh.getUrl(), payload, eventType, 1);
            }
        }
    }

    private void dispatchWithRetry(String url, String payload, String eventType, int attempt) {
        try {
            restTemplate.postForObject(url, payload, String.class);
            log.info("Webhook dispatched successfully for eventType={} url={}", eventType, url);
        } catch (RestClientException ex) {
            if (attempt < 3) {
                log.warn("Webhook dispatch failed, retrying (attempt {}) for url={} reason={}", attempt, url, ex.getMessage());
                scheduler.schedule(() -> dispatchWithRetry(url, payload, eventType, attempt + 1), 2L * attempt, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                log.error("Webhook dispatch permanently failed after 3 attempts for url={} reason={}", url, ex.getMessage());
            }
        }
    }
}
