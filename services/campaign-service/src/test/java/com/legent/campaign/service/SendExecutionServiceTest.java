package com.legent.campaign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.client.ContentServiceClient;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
                stateMachine);
        ReflectionTestUtils.setField(service, "maxRenderCacheEntries", 16);
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

    private void stubBatchExecution(SendBatch batch,
                                    Campaign campaign,
                                    List<Map<String, String>> subscribers) {
        when(batchRepository.findById("batch-1")).thenReturn(Optional.of(batch));
        when(batchRepository.saveAndFlush(any(SendBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.save(any(SendBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.countByTenantWorkspaceAndJob("tenant-1", "workspace-1", "job-1")).thenReturn(1L);
        when(batchRepository.countByTenantWorkspaceAndJobAndStatuses(anyString(), anyString(), anyString(), any()))
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
                .thenReturn(subscribers.size());
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
