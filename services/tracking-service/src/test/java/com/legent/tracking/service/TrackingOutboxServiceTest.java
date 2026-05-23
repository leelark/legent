package com.legent.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.tracking.domain.TrackingOutboxEvent;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.event.TrackingEventPublisher;
import com.legent.tracking.repository.TrackingOutboxEventRepository;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingOutboxServiceTest {

    @Mock
    private TrackingOutboxEventRepository outboxRepository;

    @Mock
    private TrackingEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private TrackingOutboxService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        meterRegistry = new SimpleMeterRegistry();
        service = new TrackingOutboxService(outboxRepository, eventPublisher, objectMapper, meterRegistry);
        service.registerBacklogMetrics();
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
    }

    @Test
    void publishPending_MarksEventPublishedAfterKafkaPublish() throws Exception {
        TrackingOutboxEvent event = event("outbox-1");
        whenClaimSucceeds("outbox-1");
        whenScopedReloadFinds(event);

        service.publishPending("outbox-1", "tenant-1", "workspace-1");

        verifyClaimAttempted("outbox-1");
        verify(eventPublisher).publishIngestedEventOrThrow(any(TrackingDto.RawEventPayload.class));
        assertEquals(TrackingOutboxEvent.STATUS_PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertEquals(1, event.getAttempts());
        assertEquals(1, meterRegistry.get("legent.tracking.outbox.publish.duration")
                .tag("outcome", "published")
                .timer()
                .count());
    }

    @Test
    void publishPending_WhenKafkaPublishFails_SchedulesRetry() throws Exception {
        TrackingOutboxEvent event = event("outbox-2");
        whenClaimSucceeds("outbox-2");
        whenScopedReloadFinds(event);
        doThrow(new IOException("kafka unavailable"))
                .when(eventPublisher).publishIngestedEventOrThrow(any(TrackingDto.RawEventPayload.class));

        service.publishPending("outbox-2", "tenant-1", "workspace-1");

        verifyClaimAttempted("outbox-2");
        assertEquals(TrackingOutboxEvent.STATUS_PENDING, event.getStatus());
        assertEquals(1, event.getAttempts());
        assertNotNull(event.getLastError());
        assertNotNull(event.getNextAttemptAt());
        assertEquals(1, meterRegistry.get("legent.tracking.outbox.publish.duration")
                .tag("outcome", "failed")
                .timer()
                .count());
    }

    @Test
    void publishPending_WhenClaimAlreadyTaken_DoesNotPublish() throws Exception {
        when(outboxRepository.claimReadyForPublish(
                eq("outbox-locked"),
                eq("tenant-1"),
                eq("workspace-1"),
                anyCollection(),
                any(Instant.class),
                eq(TrackingOutboxEvent.STATUS_PUBLISHING),
                any(Instant.class)))
                .thenReturn(0);

        service.publishPending("outbox-locked", "tenant-1", "workspace-1");

        verify(outboxRepository, never()).findByIdAndTenantIdAndWorkspaceId(
                eq("outbox-locked"),
                eq("tenant-1"),
                eq("workspace-1"));
        verify(eventPublisher, never()).publishIngestedEventOrThrow(any(TrackingDto.RawEventPayload.class));
    }

    @Test
    void publishPending_WhenScopedReloadDoesNotFindClaimedId_DoesNotPublishOrSave() throws Exception {
        whenClaimSucceeds("outbox-cross-scope");
        when(outboxRepository.findByIdAndTenantIdAndWorkspaceId("outbox-cross-scope", "tenant-1", "workspace-1"))
                .thenReturn(Optional.empty());

        service.publishPending("outbox-cross-scope", "tenant-1", "workspace-1");

        verifyClaimAttempted("outbox-cross-scope");
        verify(eventPublisher, never()).publishIngestedEventOrThrow(any(TrackingDto.RawEventPayload.class));
        verify(outboxRepository, never()).save(any(TrackingOutboxEvent.class));
    }

    @Test
    void publishPending_WhenScopeDoesNotMatch_DoesNotPublishOrSave() throws Exception {
        when(outboxRepository.claimReadyForPublish(
                eq("outbox-wrong-scope"),
                eq("tenant-1"),
                eq("workspace-1"),
                anyCollection(),
                any(Instant.class),
                eq(TrackingOutboxEvent.STATUS_PUBLISHING),
                any(Instant.class)))
                .thenReturn(0);

        service.publishPending("outbox-wrong-scope", "tenant-1", "workspace-1");

        verify(outboxRepository, never()).findByIdAndTenantIdAndWorkspaceId(
                eq("outbox-wrong-scope"),
                eq("tenant-1"),
                eq("workspace-1"));
        verify(eventPublisher, never()).publishIngestedEventOrThrow(any(TrackingDto.RawEventPayload.class));
        verify(outboxRepository, never()).save(any(TrackingOutboxEvent.class));
    }

    @Test
    void enqueue_DoesNotPublishInlineByDefault() throws Exception {
        TrackingDto.RawEventPayload payload = payload("outbox-default");

        service.enqueue(payload);

        verify(outboxRepository).save(any(TrackingOutboxEvent.class));
        verify(outboxRepository, never()).claimReadyForPublish(
                any(),
                any(),
                any(),
                anyCollection(),
                any(Instant.class),
                any(),
                any(Instant.class));
        verify(eventPublisher, never()).publishIngestedEventOrThrow(any(TrackingDto.RawEventPayload.class));
    }

    @Test
    void enqueue_CanPublishInlineWhenCompatibilityFlagEnabled() throws Exception {
        TrackingDto.RawEventPayload payload = payload("outbox-inline");
        TrackingOutboxEvent event = event("outbox-inline");
        ReflectionTestUtils.setField(service, "publishAfterCommit", true);
        whenClaimSucceeds("outbox-inline");
        whenScopedReloadFinds(event);

        service.enqueue(payload);

        verifyClaimAttempted("outbox-inline");
        verify(eventPublisher).publishIngestedEventOrThrow(any(TrackingDto.RawEventPayload.class));
    }

    @Test
    void backlogMetrics_ReportReadyDepthAndOldestReadyAgeWithoutScopeLabels() {
        Instant oldest = Instant.now().minus(Duration.ofMinutes(7));
        when(outboxRepository.countByStatusInAndNextAttemptAtLessThanEqual(anyCollection(), any(Instant.class)))
                .thenReturn(42L);
        when(outboxRepository.findOldestReadyCreatedAt(anyCollection(), any(Instant.class)))
                .thenReturn(Optional.of(oldest));

        assertEquals(42.0, meterRegistry.get("legent.outbox.ready.depth")
                .tag("queue", "tracking")
                .gauge()
                .value());
        double ageSeconds = meterRegistry.get("legent.outbox.oldest.ready.age.seconds")
                .tag("queue", "tracking")
                .gauge()
                .value();
        assertEquals(420.0, ageSeconds, 5.0);

        List<Meter> backlogMeters = meterRegistry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("legent.outbox."))
                .toList();
        assertEquals(2, backlogMeters.size());
        backlogMeters.forEach(meter -> {
            assertEquals(List.of("queue"), meter.getId().getTags().stream().map(tag -> tag.getKey()).toList());
            assertFalse(meter.getId().getTags().stream().anyMatch(tag ->
                    tag.getKey().equalsIgnoreCase("tenantId")
                            || tag.getKey().equalsIgnoreCase("workspaceId")
                            || tag.getKey().equalsIgnoreCase("email")));
        });
    }

    private TrackingOutboxEvent event(String id) throws Exception {
        TrackingDto.RawEventPayload payload = payload(id);
        TrackingOutboxEvent event = new TrackingOutboxEvent();
        event.setId(id);
        event.setTenantId(payload.getTenantId());
        event.setWorkspaceId(payload.getWorkspaceId());
        event.setEventType(payload.getEventType());
        event.setMessageId(payload.getMessageId());
        event.setPayloadJson(objectMapper.writeValueAsString(payload));
        event.setStatus(TrackingOutboxEvent.STATUS_PUBLISHING);
        event.setAttempts(1);
        event.setNextAttemptAt(Instant.now().plusSeconds(300));
        return event;
    }

    private TrackingDto.RawEventPayload payload(String id) {
        return TrackingDto.RawEventPayload.builder()
                .id(id)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .eventType("OPEN")
                .messageId("message-1")
                .timestamp(Instant.now())
                .build();
    }

    private void whenClaimSucceeds(String outboxId) {
        when(outboxRepository.claimReadyForPublish(
                eq(outboxId),
                eq("tenant-1"),
                eq("workspace-1"),
                anyCollection(),
                any(Instant.class),
                eq(TrackingOutboxEvent.STATUS_PUBLISHING),
                any(Instant.class)))
                .thenReturn(1);
    }

    private void verifyClaimAttempted(String outboxId) {
        verify(outboxRepository).claimReadyForPublish(
                eq(outboxId),
                eq("tenant-1"),
                eq("workspace-1"),
                anyCollection(),
                any(Instant.class),
                eq(TrackingOutboxEvent.STATUS_PUBLISHING),
                any(Instant.class));
    }

    private void whenScopedReloadFinds(TrackingOutboxEvent event) {
        when(outboxRepository.findByIdAndTenantIdAndWorkspaceId(event.getId(), event.getTenantId(), event.getWorkspaceId()))
                .thenReturn(Optional.of(event));
    }
}
