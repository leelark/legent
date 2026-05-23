package com.legent.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.delivery.domain.DeliveryFeedbackOutboxEvent;
import com.legent.delivery.event.DeliveryEventPublisher;
import com.legent.delivery.event.DeliveryFeedbackMessage;
import com.legent.delivery.repository.DeliveryFeedbackOutboxEventRepository;
import com.legent.kafka.model.EventEnvelope;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryFeedbackOutboxServiceTest {

    @Mock
    private DeliveryFeedbackOutboxEventRepository outboxRepository;

    @Mock
    private DeliveryEventPublisher deliveryEventPublisher;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private DeliveryFeedbackOutboxService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        meterRegistry = new SimpleMeterRegistry();
        service = new DeliveryFeedbackOutboxService(outboxRepository, deliveryEventPublisher, objectMapper, meterRegistry);
        service.registerBacklogMetrics();
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "immediatePublishEnabled", true);
    }

    @Test
    void enqueue_PersistsPendingScopedFeedbackEvent() {
        DeliveryFeedbackMessage message = message("outbox-1");
        when(outboxRepository.existsByTenantIdAndWorkspaceIdAndEventTypeAndTransitionKeyAndDeletedAtIsNull(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_SENT,
                "msg-1:SENT")).thenReturn(false);
        when(outboxRepository.save(any(DeliveryFeedbackOutboxEvent.class))).thenAnswer(invocation -> {
            DeliveryFeedbackOutboxEvent event = invocation.getArgument(0, DeliveryFeedbackOutboxEvent.class);
            event.setId("outbox-1");
            return event;
        });

        service.enqueue(message);

        ArgumentCaptor<DeliveryFeedbackOutboxEvent> eventCaptor = ArgumentCaptor.forClass(DeliveryFeedbackOutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        DeliveryFeedbackOutboxEvent saved = eventCaptor.getValue();
        assertEquals("tenant-1", saved.getTenantId());
        assertEquals("workspace-1", saved.getWorkspaceId());
        assertEquals(AppConstants.TOPIC_EMAIL_SENT, saved.getTopic());
        assertEquals("event-outbox-1", saved.getEventId());
        assertEquals("msg-1", saved.getMessageId());
        assertEquals("msg-1:SENT", saved.getTransitionKey());
        assertEquals("msg-1", saved.getPartitionKey());
        assertEquals(DeliveryFeedbackOutboxEvent.STATUS_PENDING, saved.getStatus());
        assertEquals(3, saved.getMaxAttempts());
        assertTrue(saved.getPayloadJson().contains("\"messageId\":\"msg-1\""));
    }

    @Test
    void enqueue_WhenImmediatePublishDisabled_PersistsPendingEventWithoutClaimingPublish() {
        ReflectionTestUtils.setField(service, "immediatePublishEnabled", false);
        DeliveryFeedbackMessage message = message("outbox-deferred");
        when(outboxRepository.existsByTenantIdAndWorkspaceIdAndEventTypeAndTransitionKeyAndDeletedAtIsNull(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_SENT,
                "msg-1:SENT")).thenReturn(false);
        when(outboxRepository.save(any(DeliveryFeedbackOutboxEvent.class))).thenAnswer(invocation -> {
            DeliveryFeedbackOutboxEvent event = invocation.getArgument(0, DeliveryFeedbackOutboxEvent.class);
            event.setId("outbox-deferred");
            return event;
        });

        service.enqueue(message);

        ArgumentCaptor<DeliveryFeedbackOutboxEvent> eventCaptor = ArgumentCaptor.forClass(DeliveryFeedbackOutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertEquals(DeliveryFeedbackOutboxEvent.STATUS_PENDING, eventCaptor.getValue().getStatus());
        verify(outboxRepository, never()).claimReadyForPublish(
                anyString(),
                anyString(),
                anyString(),
                anyCollection(),
                any(Instant.class),
                anyString(),
                any(Instant.class));
        verify(deliveryEventPublisher, never()).publishOrThrow(any(DeliveryFeedbackMessage.class));
    }

    @Test
    void publishPending_WhenKafkaAckSucceeds_MarksPublished() {
        DeliveryFeedbackOutboxEvent event = event("outbox-2");
        whenClaimSucceeds("outbox-2");
        whenScopedReloadFinds(event);

        service.publishPending("outbox-2", "tenant-1", "workspace-1");

        verifyClaimAttempted("outbox-2");
        verify(deliveryEventPublisher).publishOrThrow(any(DeliveryFeedbackMessage.class));
        assertEquals(DeliveryFeedbackOutboxEvent.STATUS_PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertEquals(1, meterRegistry.get("legent.delivery.feedback.outbox.publish.duration")
                .tag("eventType", AppConstants.TOPIC_EMAIL_SENT)
                .tag("outcome", "published")
                .timer()
                .count());
    }

    @Test
    void publishPending_WhenKafkaAckFails_SchedulesRetry() {
        DeliveryFeedbackOutboxEvent event = event("outbox-3");
        Instant before = Instant.now();
        whenClaimSucceeds("outbox-3");
        whenScopedReloadFinds(event);
        doThrow(new CompletionException(new IllegalStateException("kafka unavailable")))
                .when(deliveryEventPublisher).publishOrThrow(any(DeliveryFeedbackMessage.class));

        service.publishPending("outbox-3", "tenant-1", "workspace-1");

        verifyClaimAttempted("outbox-3");
        assertEquals(DeliveryFeedbackOutboxEvent.STATUS_PENDING, event.getStatus());
        assertEquals(1, event.getAttempts());
        assertTrue(event.getNextAttemptAt().isAfter(before));
        assertTrue(event.getLastError().contains("kafka unavailable"));
        assertEquals(1, meterRegistry.get("legent.delivery.feedback.outbox.publish.duration")
                .tag("eventType", AppConstants.TOPIC_EMAIL_SENT)
                .tag("outcome", "failed")
                .timer()
                .count());
    }

    @Test
    void publishPending_WhenClaimAlreadyTaken_DoesNotPublish() {
        when(outboxRepository.claimReadyForPublish(
                eq("outbox-locked"),
                eq("tenant-1"),
                eq("workspace-1"),
                anyCollection(),
                any(Instant.class),
                eq(DeliveryFeedbackOutboxEvent.STATUS_PUBLISHING),
                any(Instant.class))).thenReturn(0);

        service.publishPending("outbox-locked", "tenant-1", "workspace-1");

        verify(outboxRepository, never()).findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                eq("outbox-locked"),
                eq("tenant-1"),
                eq("workspace-1"));
        verify(deliveryEventPublisher, never()).publishOrThrow(any(DeliveryFeedbackMessage.class));
    }

    @Test
    void publishPending_WhenScopeDoesNotMatch_DoesNotPublishOrMutateStatus() {
        DeliveryFeedbackOutboxEvent event = event("outbox-wrong-scope");
        event.setStatus(DeliveryFeedbackOutboxEvent.STATUS_PENDING);
        when(outboxRepository.claimReadyForPublish(
                eq("outbox-wrong-scope"),
                eq("tenant-1"),
                eq("workspace-1"),
                anyCollection(),
                any(Instant.class),
                eq(DeliveryFeedbackOutboxEvent.STATUS_PUBLISHING),
                any(Instant.class))).thenReturn(0);

        service.publishPending("outbox-wrong-scope", "tenant-1", "workspace-1");

        assertEquals(DeliveryFeedbackOutboxEvent.STATUS_PENDING, event.getStatus());
        verify(outboxRepository, never()).findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                eq("outbox-wrong-scope"),
                eq("tenant-1"),
                eq("workspace-1"));
        verify(deliveryEventPublisher, never()).publishOrThrow(any(DeliveryFeedbackMessage.class));
        verify(outboxRepository, never()).save(any(DeliveryFeedbackOutboxEvent.class));
    }

    @Test
    void publishPending_WhenScopedReloadDoesNotFindClaimedId_DoesNotPublishOrSave() {
        whenClaimSucceeds("outbox-cross-scope");
        when(outboxRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("outbox-cross-scope", "tenant-1", "workspace-1"))
                .thenReturn(Optional.empty());

        service.publishPending("outbox-cross-scope", "tenant-1", "workspace-1");

        verifyClaimAttempted("outbox-cross-scope");
        verify(deliveryEventPublisher, never()).publishOrThrow(any(DeliveryFeedbackMessage.class));
        verify(outboxRepository, never()).save(any(DeliveryFeedbackOutboxEvent.class));
    }

    @Test
    void backlogMetrics_ReportReadyDepthAndOldestReadyAgeWithoutScopeLabels() {
        Instant oldest = Instant.now().minus(Duration.ofMinutes(12));
        when(outboxRepository.countByStatusInAndNextAttemptAtLessThanEqualAndDeletedAtIsNull(anyCollection(), any(Instant.class)))
                .thenReturn(17L);
        when(outboxRepository.findOldestReadyCreatedAt(anyCollection(), any(Instant.class)))
                .thenReturn(Optional.of(oldest));

        assertEquals(17.0, meterRegistry.get("legent.outbox.ready.depth")
                .tag("queue", "delivery_feedback")
                .gauge()
                .value());
        double ageSeconds = meterRegistry.get("legent.outbox.oldest.ready.age.seconds")
                .tag("queue", "delivery_feedback")
                .gauge()
                .value();
        assertEquals(720.0, ageSeconds, 5.0);

        List<Meter> backlogMeters = meterRegistry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("legent.outbox."))
                .toList();
        assertEquals(2, backlogMeters.size());
        backlogMeters.forEach(meter -> {
            assertEquals(List.of("queue"), meter.getId().getTags().stream().map(tag -> tag.getKey()).toList());
            assertTrue(meter.getId().getTags().stream().noneMatch(tag ->
                    tag.getKey().equalsIgnoreCase("tenantId")
                            || tag.getKey().equalsIgnoreCase("workspaceId")
                            || tag.getKey().equalsIgnoreCase("email")));
        });
    }

    private DeliveryFeedbackMessage message(String id) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.<Map<String, String>>builder()
                .eventId("event-" + id)
                .eventType(AppConstants.TOPIC_EMAIL_SENT)
                .timestamp(Instant.parse("2026-05-20T00:00:00Z"))
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .ownershipScope("WORKSPACE")
                .correlationId("cor-1")
                .source("delivery-service")
                .schemaVersion(1)
                .idempotencyKey("ik-" + id)
                .payload(Map.of(
                        "workspaceId", "workspace-1",
                        "messageId", "msg-1",
                        "campaignId", "camp-1",
                        "jobId", "job-1",
                        "batchId", "batch-1",
                        "subscriberId", "sub-1"))
                .build();
        return new DeliveryFeedbackMessage(
                AppConstants.TOPIC_EMAIL_SENT,
                "msg-1:SENT",
                "msg-1",
                "msg-1",
                "camp-1",
                "job-1",
                "batch-1",
                "sub-1",
                null,
                null,
                envelope);
    }

    private DeliveryFeedbackOutboxEvent event(String id) {
        DeliveryFeedbackOutboxEvent event = new DeliveryFeedbackOutboxEvent();
        event.setId(id);
        event.setTenantId("tenant-1");
        event.setWorkspaceId("workspace-1");
        event.setOwnershipScope("WORKSPACE");
        event.setTopic(AppConstants.TOPIC_EMAIL_SENT);
        event.setEventType(AppConstants.TOPIC_EMAIL_SENT);
        event.setEventId("event-" + id);
        event.setEventTimestamp(Instant.parse("2026-05-20T00:00:00Z"));
        event.setSource("delivery-service");
        event.setSchemaVersion(1);
        event.setCorrelationId("cor-1");
        event.setIdempotencyKey("ik-" + id);
        event.setMessageId("msg-1");
        event.setCampaignId("camp-1");
        event.setJobId("job-1");
        event.setBatchId("batch-1");
        event.setSubscriberId("sub-1");
        event.setTransitionKey("msg-1:SENT");
        event.setPartitionKey("msg-1");
        event.setPayloadJson("{\"workspaceId\":\"workspace-1\",\"messageId\":\"msg-1\",\"campaignId\":\"camp-1\",\"jobId\":\"job-1\",\"batchId\":\"batch-1\",\"subscriberId\":\"sub-1\"}");
        event.setStatus(DeliveryFeedbackOutboxEvent.STATUS_PUBLISHING);
        event.setAttempts(1);
        event.setMaxAttempts(3);
        event.setNextAttemptAt(Instant.now().plusSeconds(300));
        return event;
    }

    private void whenClaimSucceeds(String outboxId) {
        when(outboxRepository.claimReadyForPublish(
                eq(outboxId),
                eq("tenant-1"),
                eq("workspace-1"),
                anyCollection(),
                any(Instant.class),
                eq(DeliveryFeedbackOutboxEvent.STATUS_PUBLISHING),
                any(Instant.class))).thenReturn(1);
    }

    private void verifyClaimAttempted(String outboxId) {
        verify(outboxRepository).claimReadyForPublish(
                eq(outboxId),
                eq("tenant-1"),
                eq("workspace-1"),
                anyCollection(),
                any(Instant.class),
                eq(DeliveryFeedbackOutboxEvent.STATUS_PUBLISHING),
                any(Instant.class));
    }

    private void whenScopedReloadFinds(DeliveryFeedbackOutboxEvent event) {
        when(outboxRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                event.getId(),
                event.getTenantId(),
                event.getWorkspaceId()))
                .thenReturn(Optional.of(event));
    }
}
