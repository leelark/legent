package com.legent.foundation.service;

import com.legent.foundation.domain.WebhookIntegration;
import com.legent.foundation.repository.WebhookIntegrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class WebhookService {
    private final WebhookIntegrationRepository repo;
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendEvent(String eventType, String payload) {
        for (WebhookIntegration wh : repo.findAll()) {
            if (wh.getEventType().equals(eventType)) {
                restTemplate.postForObject(wh.getUrl(), payload, String.class);
            }
        }
    }
}
