package com.legent.tracking.service;

import org.mockito.ArgumentCaptor;

import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.repository.RawEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrackingIngestionServiceTest {

    @Mock
    private com.legent.cache.service.CacheService cacheService;

    @Mock
    private RawEventRepository rawEventRepository;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private TrackingOutboxService outboxService;

    @InjectMocks
    private TrackingIngestionService ingestionService;

    @Test
    void processOpen_PublishesEventAndParsesUserAgent() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        org.mockito.Mockito.when(cacheService.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(rawEventRepository.findTopByTenantIdAndWorkspaceIdAndEventTypeAndMessageIdAndSubscriberIdAndTimestampAfter(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        ingestionService.processOpen("t1", "c1", "s1", "m1", "workspace-default", "idem-1", ua, "192.168.1.1");

        ArgumentCaptor<TrackingDto.RawEventPayload> captor = ArgumentCaptor.forClass(TrackingDto.RawEventPayload.class);
        verify(outboxService).enqueue(captor.capture());

        TrackingDto.RawEventPayload payload = captor.getValue();
        assertEquals("OPEN", payload.getEventType());
        assertEquals("t1", payload.getTenantId());
        assertEquals("workspace-default", payload.getWorkspaceId());
        assertEquals("c1", payload.getCampaignId());
        assertEquals("192.168.1.1", payload.getIpAddress());
        assertNotNull(payload.getMetadata().get("browser")); // User-Agent utils should pick this up
    }

    @Test
    void processClick_PublishesEventWithUrl() {
        String url = "https://example.com/promo";
        org.mockito.Mockito.when(cacheService.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(rawEventRepository.findTopByTenantIdAndWorkspaceIdAndEventTypeAndMessageIdAndSubscriberIdAndTimestampAfter(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        ingestionService.processClick("t1", "c1", "s1", "m1", "workspace-default", "idem-2", url, null, "10.0.0.1");

        ArgumentCaptor<TrackingDto.RawEventPayload> captor = ArgumentCaptor.forClass(TrackingDto.RawEventPayload.class);
        verify(outboxService).enqueue(captor.capture());

        TrackingDto.RawEventPayload payload = captor.getValue();
        assertEquals("CLICK", payload.getEventType());
        assertEquals(url, payload.getLinkUrl());
    }

    @Test
    void processOpen_RedisDeduplicationIsScopedByWorkspace() {
        java.util.Set<String> redisKeys = new java.util.HashSet<>();
        org.mockito.Mockito.when(cacheService.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenAnswer(invocation -> redisKeys.contains(invocation.getArgument(0))
                        ? java.util.Optional.of("1")
                        : java.util.Optional.empty());
        org.mockito.Mockito.doAnswer(invocation -> {
                    redisKeys.add(invocation.getArgument(0));
                    return null;
                })
                .when(cacheService).set(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("1"),
                        org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.when(rawEventRepository.findTopByTenantIdAndWorkspaceIdAndEventTypeAndMessageIdAndSubscriberIdAndTimestampAfter(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());

        ingestionService.processOpen("tenant-1", "campaign-1", "subscriber-1", "message-1",
                "workspace-a", "idem-a", null, "192.168.1.1");
        ingestionService.processOpen("tenant-1", "campaign-1", "subscriber-1", "message-1",
                "workspace-b", "idem-b", null, "192.168.1.1");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheService, times(2)).set(
                keyCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("1"),
                org.mockito.ArgumentMatchers.any());
        assertEquals(java.util.List.of(
                "track:dedup:tenant-1:workspace-a:OPEN:message-1:subscriber-1",
                "track:dedup:tenant-1:workspace-b:OPEN:message-1:subscriber-1"), keyCaptor.getAllValues());
        verify(outboxService, times(2)).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processOpen_WhenDatabaseSaveFails_DoesNotPublishEvent() {
        org.mockito.Mockito.when(cacheService.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(rawEventRepository.findTopByTenantIdAndWorkspaceIdAndEventTypeAndMessageIdAndSubscriberIdAndTimestampAfter(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(rawEventRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("database unavailable"));

        assertThrows(IllegalStateException.class,
                () -> ingestionService.processOpen("t1", "c1", "s1", "m1", "workspace-1", "idem-3", null, "192.168.1.1"));

        verify(outboxService, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void processOpen_WhenWorkspaceCannotBeResolved_RejectsEvent() {
        org.mockito.Mockito.when(rawEventRepository.findTopByTenantIdAndMessageIdAndWorkspaceIdIsNotNullOrderByTimestampDesc("t1", "m1"))
                .thenReturn(java.util.Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> ingestionService.processOpen("t1", "c1", "s1", "m1", null, "idem-4", null, "192.168.1.1"));

        verify(outboxService, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }
}
