package com.legent.campaign.event;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
                "idem-1")).thenReturn(true);

        consumer.handleAudienceResolved(event);

        InOrder inOrder = inOrder(idempotencyService, batchingService);
        inOrder.verify(idempotencyService).registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "idem-1");
        inOrder.verify(batchingService).processResolvedAudienceChunk(
                eq("tenant-1"),
                eq("job-1"),
                anyList(),
                eq(true));
        inOrder.verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "idem-1");
        verify(idempotencyService, never()).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "idem-1");
    }

    @Test
    void handleAudienceResolvedReleasesClaimWhenBatchingFails() {
        EventEnvelope<Map<String, Object>> event = audienceResolvedEvent();
        when(idempotencyService.registerIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "idem-1")).thenReturn(true);
        doThrow(new RuntimeException("batching failed")).when(batchingService).processResolvedAudienceChunk(
                eq("tenant-1"),
                eq("job-1"),
                anyList(),
                eq(true));

        assertThrows(IllegalStateException.class, () -> consumer.handleAudienceResolved(event));

        verify(idempotencyService).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "idem-1");
        verify(idempotencyService, never()).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                "event-1",
                "idem-1");
    }

    private EventEnvelope<Map<String, Object>> audienceResolvedEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceId", "workspace-1");
        payload.put("jobId", "job-1");
        payload.put("isLastChunk", true);
        payload.put("subscribers", List.of(Map.of("email", "one@example.com", "subscriberId", "sub-1")));
        return EventEnvelope.<Map<String, Object>>builder()
                .eventId("event-1")
                .eventType(AppConstants.TOPIC_AUDIENCE_RESOLVED)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .idempotencyKey("idem-1")
                .payload(payload)
                .build();
    }
}
