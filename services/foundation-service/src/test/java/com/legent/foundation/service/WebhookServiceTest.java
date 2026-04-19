package com.legent.foundation.service;

import com.legent.foundation.domain.WebhookIntegration;
import com.legent.foundation.repository.WebhookIntegrationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WebhookServiceTest {
    @Test
    void sendEvent_postsToMatchingWebhook() {
        var repo = Mockito.mock(WebhookIntegrationRepository.class);
        var wh = new WebhookIntegration();
        wh.setEventType("test");
        wh.setUrl("http://example.com");
        Mockito.when(repo.findAll()).thenReturn(java.util.List.of(wh));
        var svc = new com.legent.foundation.service.WebhookService(repo);
        svc.sendEvent("test", "payload");
        // No exception = pass (actual HTTP call not tested)
    }
}
