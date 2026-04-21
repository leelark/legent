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
                try {
                    restTemplate.postForObject(wh.getUrl(), payload, String.class);
                    log.info("Webhook dispatched successfully for eventType={} url={}", eventType, wh.getUrl());
                } catch (RestClientException ex) {
                    log.warn("Webhook dispatch failed for eventType={} url={} reason={}",
                            eventType,
                            wh.getUrl(),
                            ex.getMessage());
                }
            }
        }
    }
}
