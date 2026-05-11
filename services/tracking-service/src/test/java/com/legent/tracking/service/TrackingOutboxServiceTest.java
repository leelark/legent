package com.legent.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.tracking.domain.TrackingOutboxEvent;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.event.TrackingEventPublisher;
import com.legent.tracking.repository.TrackingOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingOutboxServiceTest {

    @Mock
    private TrackingOutboxEventRepository outboxRepository;

    @Mock
    private TrackingEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private TrackingOutboxService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new TrackingOutboxService(outboxRepository, eventPublisher, objectMapper);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
    }

    @Test
    void publishPending_MarksEventPublishedAfterKafkaPublish() throws Exception {
        TrackingOutboxEvent event = event("outbox-1");
        when(outboxRepository.findById("outbox-1")).thenReturn(Optional.of(event));

        service.publishPending("outbox-1");

        verify(eventPublisher).publishIngestedEventOrThrow(any(TrackingDto.RawEventPayload.class));
        assertEquals(TrackingOutboxEvent.STATUS_PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertEquals(1, event.getAttempts());
    }

    @Test
    void publishPending_WhenKafkaPublishFails_SchedulesRetry() throws Exception {
        TrackingOutboxEvent event = event("outbox-2");
        when(outboxRepository.findById("outbox-2")).thenReturn(Optional.of(event));
        doThrow(new IOException("kafka unavailable"))
                .when(eventPublisher).publishIngestedEventOrThrow(any(TrackingDto.RawEventPayload.class));

        service.publishPending("outbox-2");

        assertEquals(TrackingOutboxEvent.STATUS_PENDING, event.getStatus());
        assertEquals(1, event.getAttempts());
        assertNotNull(event.getLastError());
        assertNotNull(event.getNextAttemptAt());
    }

    private TrackingOutboxEvent event(String id) throws Exception {
        TrackingDto.RawEventPayload payload = TrackingDto.RawEventPayload.builder()
                .id(id)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .eventType("OPEN")
                .messageId("message-1")
                .timestamp(Instant.now())
                .build();
        TrackingOutboxEvent event = new TrackingOutboxEvent();
        event.setId(id);
        event.setTenantId(payload.getTenantId());
        event.setWorkspaceId(payload.getWorkspaceId());
        event.setEventType(payload.getEventType());
        event.setMessageId(payload.getMessageId());
        event.setPayloadJson(objectMapper.writeValueAsString(payload));
        event.setStatus(TrackingOutboxEvent.STATUS_PENDING);
        event.setNextAttemptAt(Instant.now().minusSeconds(1));
        return event;
    }
}
