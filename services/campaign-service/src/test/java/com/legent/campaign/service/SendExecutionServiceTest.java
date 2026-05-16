package com.legent.campaign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.client.ContentServiceClient;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.constant.AppConstants;
import com.legent.common.event.EmailContentReference;
import com.legent.common.exception.ValidationException;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendExecutionServiceTest {

    @Mock private SendBatchRepository batchRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private SendJobRepository sendJobRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private ThrottlingService throttlingService;
    @Mock private ContentServiceClient contentServiceClient;
    @Mock private CampaignSendSafetyService sendSafetyService;
    @Mock private CampaignStateMachineService stateMachine;
    @Mock private RenderedContentReferenceService contentReferenceService;

    private SendExecutionService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        service = new SendExecutionService(
                batchRepository,
                campaignRepository,
                sendJobRepository,
                eventPublisher,
                objectMapper,
                throttlingService,
                contentServiceClient,
                sendSafetyService,
                stateMachine,
                contentReferenceService);
        ReflectionTestUtils.setField(service, "maxRenderCacheEntries", 16);
        ReflectionTestUtils.setField(service, "maxEmailPayloadBytes", 1024 * 1024);
        ReflectionTestUtils.setField(service, "contentReferenceEnabled", true);
        ReflectionTestUtils.setField(service, "includeInlineRenderedContent", true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void reusesRenderedContentForDuplicateRenderInputsWithinBatch() throws Exception {
        SendBatch batch = batch();
        Campaign campaign = campaign();
        List<Map<String, String>> subscribers = List.of(
                Map.of("email", "same@example.com", "subscriberId", "sub-1", "firstName", "Asha"),
                Map.of("email", "same@example.com", "subscriberId", "sub-1", "firstName", "Asha"));

        stubBatchExecution(batch, campaign, subscribers);
        when(contentServiceClient.renderTemplate(eq("tenant-1"), eq("content-1"), any()))
                .thenReturn(new ContentServiceClient.RenderedContent("Rendered", "<p>Hello Asha</p>", "Hello Asha"));

        service.executeBatch("tenant-1", "job-1", "batch-1", objectMapper.writeValueAsString(subscribers));

        verify(contentServiceClient, times(1)).renderTemplate(eq("tenant-1"), eq("content-1"), any());
        verify(eventPublisher, times(2)).publish(anyString(), any());
    }

    @Test
    void doesNotReuseRenderedContentWhenPersonalizationVariablesDiffer() throws Exception {
        SendBatch batch = batch();
        Campaign campaign = campaign();
        List<Map<String, String>> subscribers = List.of(
                Map.of("email", "one@example.com", "subscriberId", "sub-1", "firstName", "Asha"),
                Map.of("email", "two@example.com", "subscriberId", "sub-2", "firstName", "Ishan"));

        stubBatchExecution(batch, campaign, subscribers);
        when(contentServiceClient.renderTemplate(eq("tenant-1"), eq("content-1"), any()))
                .thenReturn(new ContentServiceClient.RenderedContent("Rendered", "<p>Hello</p>", "Hello"));

        service.executeBatch("tenant-1", "job-1", "batch-1", objectMapper.writeValueAsString(subscribers));

        verify(contentServiceClient, times(2)).renderTemplate(eq("tenant-1"), eq("content-1"), any());
        verify(eventPublisher, times(2)).publish(anyString(), any());
    }

    @Test
    void clearsBatchPayloadWhenBatchCompletes() throws Exception {
        SendBatch batch = batch();
        Campaign campaign = campaign();
        List<Map<String, String>> subscribers = List.of(
                Map.of("email", "one@example.com", "subscriberId", "sub-1"),
                Map.of("email", "two@example.com", "subscriberId", "sub-2"));
        batch.setPayload(objectMapper.writeValueAsString(subscribers));

        stubBatchExecution(batch, campaign, subscribers);
        when(contentServiceClient.renderTemplate(eq("tenant-1"), eq("content-1"), any()))
                .thenReturn(new ContentServiceClient.RenderedContent("Rendered", "<p>Hello</p>", "Hello"));

        service.executeBatch("tenant-1", "job-1", "batch-1", batch.getPayload());

        assertEquals(SendBatch.BatchStatus.COMPLETED, batch.getStatus());
        assertEquals("[]", batch.getPayload());
        verify(batchRepository, times(1)).save(batch);
    }

    @Test
    void preservesRemainingPayloadWhenBatchIsPartial() throws Exception {
        SendBatch batch = batch();
        Campaign campaign = campaign();
        List<Map<String, String>> subscribers = List.of(
                Map.of("email", "one@example.com", "subscriberId", "sub-1"),
                Map.of("email", "two@example.com", "subscriberId", "sub-2"));
        batch.setPayload(objectMapper.writeValueAsString(subscribers));

        stubBatchExecution(batch, campaign, subscribers, 1);
        when(contentServiceClient.renderTemplate(eq("tenant-1"), eq("content-1"), any()))
                .thenReturn(new ContentServiceClient.RenderedContent("Rendered", "<p>Hello</p>", "Hello"));

        service.executeBatch("tenant-1", "job-1", "batch-1", batch.getPayload());

        assertEquals(SendBatch.BatchStatus.PARTIAL, batch.getStatus());
        List<Map<String, String>> remaining = objectMapper.readValue(
                batch.getPayload(), new TypeReference<>() {});
        assertEquals(List.of(Map.of("email", "two@example.com", "subscriberId", "sub-2")), remaining);
        verify(batchRepository, times(1)).save(batch);
    }

    @Test
    void retainsFailedPublishPayloadAndLeavesDeliveryCountersForFeedback() throws Exception {
        SendBatch batch = batch();
        Campaign campaign = campaign();
        List<Map<String, String>> subscribers = List.of(
                Map.of("email", "one@example.com", "subscriberId", "sub-1"),
                Map.of("email", "two@example.com", "subscriberId", "sub-2"));
        batch.setPayload(objectMapper.writeValueAsString(subscribers));

        stubBatchExecution(batch, campaign, subscribers);
        when(contentServiceClient.renderTemplate(eq("tenant-1"), eq("content-1"), any()))
                .thenReturn(new ContentServiceClient.RenderedContent("Rendered", "<p>Hello</p>", "Hello"));
        when(eventPublisher.publish(eq(AppConstants.TOPIC_EMAIL_SEND_REQUESTED), any()))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));
        when(eventPublisher.publish(eq(AppConstants.TOPIC_SEND_FAILED), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.executeBatch("tenant-1", "job-1", "batch-1", batch.getPayload());

        assertEquals(SendBatch.BatchStatus.PARTIAL, batch.getStatus());
        assertEquals(0, batch.getProcessedCount());
        assertEquals(0, batch.getSuccessCount());
        assertEquals(0, batch.getFailureCount());
        List<Map<String, String>> retryPayload = objectMapper.readValue(
                batch.getPayload(), new TypeReference<>() {});
        assertEquals(List.of(Map.of("email", "two@example.com", "subscriberId", "sub-2")), retryPayload);
    }

    @Test
    void externalizesOversizedRenderedPayloadBeforePublishingSendRequest() throws Exception {
        SendBatch batch = batch();
        batch.setPayload(objectMapper.writeValueAsString(List.of(
                Map.of("email", "one@example.com", "subscriberId", "sub-1"))));
        Campaign campaign = campaign();
        stubBatchExecution(batch, campaign, List.of(Map.of("email", "one@example.com", "subscriberId", "sub-1")));
        ReflectionTestUtils.setField(service, "maxEmailPayloadBytes", 1200);
        when(contentServiceClient.renderTemplate(eq("tenant-1"), eq("content-1"), any()))
                .thenReturn(new ContentServiceClient.RenderedContent(
                        "Rendered",
                        "<p>" + "x".repeat(2048) + "</p>",
                        "Hello"));

        service.executeBatch("tenant-1", "job-1", "batch-1", batch.getPayload());

        assertEquals(SendBatch.BatchStatus.COMPLETED, batch.getStatus());
        assertEquals("[]", batch.getPayload());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<Map<String, Object>>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_EMAIL_SEND_REQUESTED), captor.capture());
        Map<String, Object> payload = captor.getValue().getPayload();
        assertEquals("content-ref-1", payload.get("contentReference"));
        assertFalse(payload.containsKey("htmlBody"));
        assertFalse(payload.containsKey("textBody"));
        EmailContentReference metadata = (EmailContentReference) payload.get("contentReferenceMetadata");
        assertNotNull(metadata);
        assertEquals(Boolean.FALSE, metadata.getInlineFallbackIncluded());
        verify(eventPublisher, never()).publish(eq(AppConstants.TOPIC_SEND_FAILED), any());
        verify(sendSafetyService, never()).recordDeliveryFeedback(anyString(), anyString(), anyString(), eq(true), anyString());
        verify(contentReferenceService).createReference(any(), eq(false));
    }

    @Test
    void publishesReferenceOnlyPayloadWhenInlineContentDisabled() throws Exception {
        SendBatch batch = batch();
        batch.setPayload(objectMapper.writeValueAsString(List.of(
                Map.of("email", "one@example.com", "subscriberId", "sub-1"))));
        Campaign campaign = campaign();
        stubBatchExecution(batch, campaign, List.of(Map.of("email", "one@example.com", "subscriberId", "sub-1")));
        ReflectionTestUtils.setField(service, "includeInlineRenderedContent", false);
        when(contentServiceClient.renderTemplate(eq("tenant-1"), eq("content-1"), any()))
                .thenReturn(new ContentServiceClient.RenderedContent("Rendered", "<p>Hello</p>", "Hello"));

        service.executeBatch("tenant-1", "job-1", "batch-1", batch.getPayload());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<Map<String, Object>>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_EMAIL_SEND_REQUESTED), captor.capture());
        Map<String, Object> payload = captor.getValue().getPayload();
        assertEquals("content-ref-1", payload.get("contentReference"));
        assertFalse(payload.containsKey("htmlBody"));
        assertFalse(payload.containsKey("textBody"));
        EmailContentReference metadata = (EmailContentReference) payload.get("contentReferenceMetadata");
        assertNotNull(metadata);
        assertEquals(Boolean.FALSE, metadata.getInlineFallbackIncluded());
        verify(contentReferenceService).createReference(any(), eq(false));
    }

    @Test
    void failsClosedWhenContentReferenceCreationFailsWithInlineFallbackConfigured() throws Exception {
        SendBatch batch = batch();
        batch.setPayload(objectMapper.writeValueAsString(List.of(
                Map.of("email", "one@example.com", "subscriberId", "sub-1"))));
        Campaign campaign = campaign();
        stubBatchExecution(batch, campaign, List.of(Map.of("email", "one@example.com", "subscriberId", "sub-1")));
        when(contentServiceClient.renderTemplate(eq("tenant-1"), eq("content-1"), any()))
                .thenReturn(new ContentServiceClient.RenderedContent("Rendered", "<p>Hello</p>", "Hello"));
        when(contentReferenceService.createReference(any(), eq(true)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        service.executeBatch("tenant-1", "job-1", "batch-1", batch.getPayload());

        assertEquals(SendBatch.BatchStatus.FAILED, batch.getStatus());
        assertEquals("Rendered content reference unavailable", batch.getLastError());
        assertEquals("[]", batch.getPayload());
        verify(eventPublisher, never()).publish(eq(AppConstants.TOPIC_EMAIL_SEND_REQUESTED), any());
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_SEND_FAILED), any());
        verify(sendSafetyService).recordDeliveryFeedback(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("job-1:batch-1:sub-1:r0"),
                eq(true),
                org.mockito.ArgumentMatchers.contains("CONTENT_REFERENCE_UNAVAILABLE"));
        verify(contentReferenceService).createReference(
                argThat(request -> "tenant-1".equals(request.tenantId())
                        && "workspace-1".equals(request.workspaceId())
                        && "campaign-1".equals(request.campaignId())
                        && "job-1".equals(request.jobId())
                        && "batch-1".equals(request.batchId())
                        && "job-1:batch-1:sub-1:r0".equals(request.messageId())
                        && "content-1".equals(request.contentId())
                        && "<p>Hello</p>".equals(request.htmlBody())),
                eq(true));
    }

    @Test
    void rethrowsUnexpectedExecutionFailureAfterProcessingState() throws Exception {
        SendBatch batch = batch();
        Campaign campaign = campaign();
        List<Map<String, String>> subscribers = List.of(
                Map.of("email", "one@example.com", "subscriberId", "sub-1"));
        batch.setPayload(objectMapper.writeValueAsString(subscribers));

        stubBatchExecution(batch, campaign, subscribers);
        when(contentServiceClient.renderTemplate(eq("tenant-1"), eq("content-1"), any()))
                .thenThrow(new RuntimeException("render failed"));

        assertThrows(IllegalStateException.class,
                () -> service.executeBatch("tenant-1", "job-1", "batch-1", batch.getPayload()));

        assertEquals(SendBatch.BatchStatus.PROCESSING, batch.getStatus());
        verify(batchRepository).saveAndFlush(batch);
        verify(batchRepository, never()).save(batch);
    }

    @Test
    void reconcilesJobAndCampaignWhenBatchFailsTerminally() throws Exception {
        SendBatch batch = batch();
        batch.setBatchSize(1);
        batch.setPayload(objectMapper.writeValueAsString(List.of(
                Map.of("email", "one@example.com", "subscriberId", "sub-1"))));
        Campaign campaign = campaign();
        campaign.setContentId(null);
        campaign.setStatus(Campaign.CampaignStatus.SENDING);
        SendJob job = new SendJob();
        job.setId("job-1");
        job.setTenantId("tenant-1");
        job.setWorkspaceId("workspace-1");
        job.setCampaignId("campaign-1");
        job.setStatus(SendJob.JobStatus.SENDING);

        when(batchRepository.findById("batch-1")).thenReturn(Optional.of(batch));
        when(batchRepository.saveAndFlush(any(SendBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(any(SendBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-1", "campaign-1"))
                .thenReturn(Optional.of(campaign));
        when(eventPublisher.publish(eq(AppConstants.TOPIC_SEND_FAILED), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(batchRepository.countByTenantWorkspaceAndJob("tenant-1", "workspace-1", "job-1"))
                .thenReturn(1L);
        when(batchRepository.countByTenantWorkspaceAndJobAndStatuses(
                eq("tenant-1"), eq("workspace-1"), eq("job-1"), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<SendBatch.BatchStatus> statuses = invocation.getArgument(3);
                    return statuses.contains(SendBatch.BatchStatus.FAILED) ? 1L : 0L;
                });
        when(sendJobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "job-1")).thenReturn(Optional.of(job));
        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "campaign-1")).thenReturn(Optional.of(campaign));
        doAnswer(invocation -> {
            SendJob target = invocation.getArgument(0);
            SendJob.JobStatus status = invocation.getArgument(1);
            target.setStatus(status);
            target.setErrorMessage(invocation.getArgument(2));
            return null;
        }).when(stateMachine).transitionJob(any(SendJob.class), any(SendJob.JobStatus.class), anyString());
        doAnswer(invocation -> {
            Campaign target = invocation.getArgument(0);
            Campaign.CampaignStatus status = invocation.getArgument(1);
            target.setStatus(status);
            target.setLifecycleNote(invocation.getArgument(2));
            return null;
        }).when(stateMachine).transitionCampaign(any(Campaign.class), any(Campaign.CampaignStatus.class), anyString());

        service.executeBatch("tenant-1", "job-1", "batch-1", batch.getPayload());

        assertEquals(SendBatch.BatchStatus.FAILED, batch.getStatus());
        assertEquals(SendJob.JobStatus.FAILED, job.getStatus());
        assertEquals(Campaign.CampaignStatus.FAILED, campaign.getStatus());
        verify(sendJobRepository).save(job);
        verify(campaignRepository).save(campaign);
    }

    private void stubBatchExecution(SendBatch batch,
                                    Campaign campaign,
                                    List<Map<String, String>> subscribers) {
        stubBatchExecution(batch, campaign, subscribers, subscribers.size());
    }

    private void stubBatchExecution(SendBatch batch,
                                    Campaign campaign,
                                    List<Map<String, String>> subscribers,
                                    int acquiredPermits) {
        when(batchRepository.findById("batch-1")).thenReturn(Optional.of(batch));
        when(batchRepository.saveAndFlush(any(SendBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(batchRepository.save(any(SendBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(batchRepository.countByTenantWorkspaceAndJob("tenant-1", "workspace-1", "job-1")).thenReturn(1L);
        lenient().when(batchRepository.countByTenantWorkspaceAndJobAndStatuses(anyString(), anyString(), anyString(), any()))
                .thenReturn(1L);
        when(campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull("tenant-1", "campaign-1"))
                .thenReturn(Optional.of(campaign));
        when(sendSafetyService.buildPlan(campaign))
                .thenReturn(new CampaignSendSafetyService.SendPlan(null, List.of(), null, null));
        doAnswer(invocation -> {
            Map<String, String> subscriber = invocation.getArgument(3);
            String messageId = invocation.getArgument(4);
            return CampaignSendSafetyService.PreparedRecipient.send(
                    subscriber,
                    messageId,
                    null,
                    null,
                    null,
                    null,
                    BigDecimal.ZERO);
        }).when(sendSafetyService).prepareRecipient(eq(campaign), eq(batch), any(), any(), anyString());
        when(throttlingService.acquirePermits(eq("tenant-1"), eq("example.com"), anyInt()))
                .thenReturn(acquiredPermits);
        lenient().when(contentReferenceService.createReference(any(), anyBoolean()))
                .thenAnswer(invocation -> EmailContentReference.builder()
                        .referenceId("content-ref-1")
                        .storageBackend("redis")
                        .tenantId("tenant-1")
                        .workspaceId("workspace-1")
                        .campaignId("campaign-1")
                        .jobId("job-1")
                        .batchId("batch-1")
                        .messageId("message-1")
                        .contentId("content-1")
                        .inlineFallbackIncluded(invocation.getArgument(1))
                        .build());
        lenient().when(eventPublisher.publish(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private SendBatch batch() {
        SendBatch batch = new SendBatch();
        batch.setId("batch-1");
        batch.setTenantId("tenant-1");
        batch.setWorkspaceId("workspace-1");
        batch.setCampaignId("campaign-1");
        batch.setJobId("job-1");
        batch.setDomain("example.com");
        batch.setBatchSize(2);
        batch.setProcessedCount(0);
        batch.setSuccessCount(0);
        batch.setFailureCount(0);
        batch.setRetryCount(0);
        return batch;
    }

    private Campaign campaign() {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setTenantId("tenant-1");
        campaign.setWorkspaceId("workspace-1");
        campaign.setName("Campaign");
        campaign.setContentId("content-1");
        campaign.setSubject("Campaign subject");
        campaign.setSenderEmail("sender@example.com");
        campaign.setSenderName("Sender");
        campaign.setReplyToEmail("reply@example.com");
        return campaign;
    }
}
