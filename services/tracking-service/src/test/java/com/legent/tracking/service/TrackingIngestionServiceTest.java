package com.legent.tracking.service;

import org.mockito.ArgumentCaptor;

import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.event.TrackingEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrackingIngestionServiceTest {

    @Mock
    private TrackingEventPublisher eventPublisher;

    @Mock
    private com.legent.cache.service.CacheService cacheService;

    @InjectMocks
    private TrackingIngestionService ingestionService;

    @Test
    void processOpen_PublishesEventAndParsesUserAgent() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        org.mockito.Mockito.when(cacheService.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(java.util.Optional.empty());
        ingestionService.processOpen("t1", "c1", "s1", "m1", ua, "192.168.1.1");

        ArgumentCaptor<TrackingDto.RawEventPayload> captor = ArgumentCaptor.forClass(TrackingDto.RawEventPayload.class);
        verify(eventPublisher).publishIngestedEvent(captor.capture());

        TrackingDto.RawEventPayload payload = captor.getValue();
        assertEquals("OPEN", payload.getEventType());
        assertEquals("t1", payload.getTenantId());
        assertEquals("c1", payload.getCampaignId());
        assertEquals("192.168.1.1", payload.getIpAddress());
        assertNotNull(payload.getMetadata().get("browser")); // User-Agent utils should pick this up
    }

    @Test
    void processClick_PublishesEventWithUrl() {
        String url = "https://example.com/promo";
        org.mockito.Mockito.when(cacheService.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(java.util.Optional.empty());
        ingestionService.processClick("t1", "c1", "s1", "m1", url, null, "10.0.0.1");

        ArgumentCaptor<TrackingDto.RawEventPayload> captor = ArgumentCaptor.forClass(TrackingDto.RawEventPayload.class);
        verify(eventPublisher).publishIngestedEvent(captor.capture());

        TrackingDto.RawEventPayload payload = captor.getValue();
        assertEquals("CLICK", payload.getEventType());
        assertEquals(url, payload.getLinkUrl());
    }
}
