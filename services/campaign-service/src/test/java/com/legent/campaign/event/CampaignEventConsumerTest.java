package com.legent.campaign.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.dto.CampaignDto;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.campaign.service.BatchingService;
import com.legent.campaign.service.CampaignEventIdempotencyService;
import com.legent.campaign.service.CampaignMetricsService;
import com.legent.campaign.service.CampaignSendSafetyService;
import com.legent.campaign.service.CampaignStateMachineService;
import com.legent.campaign.service.OrchestrationService;
import com.legent.campaign.service.SendExecutionService;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignEventConsumerTest {

    @Mock private BatchingService batchingService;
    @Mock private SendExecutionService executionService;
    @Mock private OrchestrationService orchestrationService;
    @Mock private CampaignEventIdempotencyService idempotencyService;
    @Mock private SendJobRepository sendJobRepository;
    @Mock private SendBatchRepository sendBatchRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignStateMachineService stateMachine;
    @Mock private CampaignSendSafetyService sendSafetyService;
    @Mock private CampaignMetricsService metricsService;

    private CampaignEventConsumer consumer;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        consumer = new CampaignEventConsumer(
                batchingService,
                executionService,
                new ObjectMapper(),
                orchestrationService,
                idempotencyService,
                sendJobRepository,
                sendBatchRepository,
                campaignRepository,
                stateMachine,
                sendSafetyService,
                metricsService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void handleAudienceResolvedMarksProcessedOnlyAfterBatchingCompletes() {
        EventEnvelope<Map<String, Object>> event = audienceResolvedEvent();
        when(idempotencyService.registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "event-1")).thenReturn(true);

        consumer.handleAudienceResolved(event);

        InOrder inOrder = inOrder(idempotencyService, batchingService);
        inOrder.verify(idempotencyService).registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "event-1");
        inOrder.verify(batchingService).processResolvedAudienceChunk(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("job-1"),
                anyList(),
                eq(true));
        inOrder.verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "event-1");
        verify(idempotencyService, never()).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "event-1");
    }

    @Test
    void handleAudienceResolvedReleasesClaimWhenBatchingFails() {
        EventEnvelope<Map<String, Object>> event = audienceResolvedEvent();
        when(idempotencyService.registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "event-1")).thenReturn(true);
        doThrow(new RuntimeException("batching failed")).when(batchingService).processResolvedAudienceChunk(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("job-1"),
                anyList(),
                eq(true));

        assertThrows(IllegalStateException.class, () -> consumer.handleAudienceResolved(event));

        verify(idempotencyService).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "event-1");
        verify(idempotencyService, never()).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "event-1");
    }

    @Test
    void handleAudienceResolvedUsesChunkIdForIdempotencyAcrossNewEnvelopes() {
        EventEnvelope<Map<String, Object>> first = audienceResolvedEvent("event-1", "chunk-job-1-0");
        EventEnvelope<Map<String, Object>> duplicate = audienceResolvedEvent("event-2", "chunk-job-1-0");
        when(idempotencyService.registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "chunk-job-1-0",
                "chunk-job-1-0")).thenReturn(true, false);

        consumer.handleAudienceResolved(first);
        consumer.handleAudienceResolved(duplicate);

        verify(batchingService, times(1)).processResolvedAudienceChunk(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("job-1"),
                anyList(),
                eq(true));
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "chunk-job-1-0",
                "chunk-job-1-0");
    }

    @Test
    void handleSendProcessingAcceptsJsonStringPayload() {
        EventEnvelope<String> event = EventEnvelope.<String>builder()
                .eventId("processing-event-1")
                .eventType(AppConstants.TOPIC_SEND_PROCESSING)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .idempotencyKey("idem-processing-1")
                .payload("""
                        {"workspaceId":"workspace-1","jobId":"job-1","batchId":"batch-1"}
                        """)
                .build();
        when(idempotencyService.registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_SEND_PROCESSING,
                "processing-event-1",
                "idem-processing-1")).thenReturn(true);

        consumer.handleSendProcessing(event);

        verify(executionService).executeBatch("tenant-1", "job-1", "batch-1", null);
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_SEND_PROCESSING,
                "processing-event-1",
                "idem-processing-1");
    }

    @Test
    void handleBatchCreatedAppliesEnvironmentContextBeforeExecution() {
        EventEnvelope<Map<String, String>> event = EventEnvelope.<Map<String, String>>builder()
                .eventId("batch-event-1")
                .eventType(AppConstants.TOPIC_BATCH_CREATED)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .environmentId("environment-1")
                .correlationId("correlation-1")
                .idempotencyKey("idem-batch-1")
                .payload(Map.of(
                        "workspaceId", "workspace-1",
                        "jobId", "job-1",
                        "batchId", "batch-1"))
                .build();
        when(idempotencyService.registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_BATCH_CREATED,
                "batch-event-1",
                "idem-batch-1")).thenReturn(true);
        doAnswer(invocation -> {
            assertEquals("environment-1", TenantContext.getEnvironmentId());
            assertEquals("correlation-1", TenantContext.getCorrelationId());
            return null;
        }).when(executionService).executeBatch("tenant-1", "job-1", "batch-1", null);

        consumer.handleBatchCreated(event);

        verify(executionService).executeBatch("tenant-1", "job-1", "batch-1", null);
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_BATCH_CREATED,
                "batch-event-1",
                "idem-batch-1");
    }

    @Test
    void handleAudienceResolvedPassesResolvedPayloadWorkspaceToBatchingService() {
        EventEnvelope<Map<String, Object>> event = audienceResolvedEvent("event-payload-workspace", null, null);
        when(idempotencyService.registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-payload-workspace",
                "event-payload-workspace")).thenReturn(true);

        consumer.handleAudienceResolved(event);

        verify(batchingService).processResolvedAudienceChunk(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("job-1"),
                anyList(),
                eq(true));
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-payload-workspace",
                "event-payload-workspace");
    }

    @Test
    void handleAutomationSendRequestAcceptsPublisherCompatibleMapPayload() {
        EventEnvelope<Map<String, String>> event = EventEnvelope.<Map<String, String>>builder()
                .eventId("send-event-1")
                .eventType(AppConstants.TOPIC_SEND_REQUESTED)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .idempotencyKey("idem-send-1")
                .payload(Map.of(
                        "campaignId", "campaign-1",
                        "jobId", "job-1",
                        "workspaceId", "workspace-1",
                        "scheduledAt", "2026-05-14T00:00:00Z"))
                .build();
        when(idempotencyService.registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_SEND_REQUESTED,
                "send-event-1",
                "idem-send-1")).thenReturn(true);

        consumer.handleAutomationSendRequest(event);

        ArgumentCaptor<CampaignDto.TriggerLaunchRequest> requestCaptor =
                ArgumentCaptor.forClass(CampaignDto.TriggerLaunchRequest.class);
        verify(orchestrationService).triggerFromAutomation(eq("campaign-1"), requestCaptor.capture());
        assertEquals("AUTOMATION", requestCaptor.getValue().getTriggerSource());
        assertEquals("idem-send-1", requestCaptor.getValue().getIdempotencyKey());
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_SEND_REQUESTED,
                "send-event-1",
                "idem-send-1");
        verify(idempotencyService, never()).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_SEND_REQUESTED,
                "send-event-1",
                "idem-send-1");
    }

    @Test
    void handleAutomationSendRequestAcceptsJsonStringPayload() {
        EventEnvelope<String> event = EventEnvelope.<String>builder()
                .eventId("send-event-2")
                .eventType(AppConstants.TOPIC_SEND_REQUESTED)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .idempotencyKey("idem-send-2")
                .payload("""
                        {"campaignId":"campaign-2","workspaceId":"workspace-1","triggerSource":"WORKFLOW","instanceId":"instance-1"}
                        """)
                .build();
        when(idempotencyService.registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_SEND_REQUESTED,
                "send-event-2",
                "idem-send-2")).thenReturn(true);

        consumer.handleAutomationSendRequest(event);

        ArgumentCaptor<CampaignDto.TriggerLaunchRequest> requestCaptor =
                ArgumentCaptor.forClass(CampaignDto.TriggerLaunchRequest.class);
        verify(orchestrationService).triggerFromAutomation(eq("campaign-2"), requestCaptor.capture());
        assertEquals("WORKFLOW", requestCaptor.getValue().getTriggerSource());
        assertEquals("instance-1", requestCaptor.getValue().getTriggerReference());
        assertEquals("idem-send-2", requestCaptor.getValue().getIdempotencyKey());
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_SEND_REQUESTED,
                "send-event-2",
                "idem-send-2");
    }

    private EventEnvelope<Map<String, Object>> audienceResolvedEvent() {
        return audienceResolvedEvent("event-1", null);
    }

    private EventEnvelope<Map<String, Object>> audienceResolvedEvent(String eventId, String chunkId) {
        return audienceResolvedEvent(eventId, chunkId, "workspace-1");
    }

    private EventEnvelope<Map<String, Object>> audienceResolvedEvent(String eventId, String chunkId, String envelopeWorkspaceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceId", "workspace-1");
        payload.put("jobId", "job-1");
        payload.put("isLastChunk", true);
        payload.put("subscribers", List.of(Map.of("email", "one@example.com", "subscriberId", "sub-1")));
        if (chunkId != null) {
            payload.put("chunkId", chunkId);
            payload.put("chunkIndex", 0);
        }
        return EventEnvelope.<Map<String, Object>>builder()
                .eventId(eventId)
                .eventType(AppConstants.TOPIC_AUDIENCE_RESOLVED)
                .tenantId("tenant-1")
                .workspaceId(envelopeWorkspaceId)
                .idempotencyKey("idem-1")
                .payload(payload)
                .build();
    }
}
