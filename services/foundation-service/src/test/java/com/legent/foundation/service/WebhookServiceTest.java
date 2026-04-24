package com.legent.foundation.service;

import com.legent.foundation.domain.WebhookIntegration;
import com.legent.foundation.repository.WebhookIntegrationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

class WebhookServiceTest {

    @Test
    void sendEvent_postsToMatchingWebhook() {
        var repo = Mockito.mock(WebhookIntegrationRepository.class);
        var restTemplate = Mockito.mock(RestTemplate.class);
        var wh = new WebhookIntegration();
        wh.setEventType("test");
        wh.setUrl("http://example.com");
        Mockito.when(repo.findByEventType("test")).thenReturn(java.util.List.of(wh));
        var svc = new com.legent.foundation.service.WebhookService(repo, restTemplate);
        svc.sendEvent("test", "payload");

        Mockito.verify(restTemplate).postForObject("http://example.com", "payload", String.class);
    }
}
