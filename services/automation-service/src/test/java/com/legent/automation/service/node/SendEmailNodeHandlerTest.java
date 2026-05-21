package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.automation.service.AutomationEventIdempotencyService;
import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendEmailNodeHandlerTest {

    @Mock private WorkflowEventPublisher eventPublisher;
    @Mock private AutomationEventIdempotencyService idempotencyService;

    private SendEmailNodeHandler handler;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        TenantContext.setUserId("user-1");
        handler = new SendEmailNodeHandler(eventPublisher, idempotencyService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void executePublishesGovernedCampaignHandoff() {
        WorkflowInstance instance = workflowInstance();
        WorkflowGraphDto.WorkflowNode node = sendNode(Map.of("campaignId", "campaign-1"));
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                "workflow.action.send_email",
                "instance-1:send-node",
                "instance-1:send-node"))
                .thenReturn(true);

        String nextNode = handler.execute(instance, node);

        assertThat(nextNode).isEqualTo("next-node");
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publishAction(
                eq(AppConstants.TOPIC_SEND_REQUESTED),
                eq("tenant-1"),
                eq("instance-1:send-node"),
                payloadCaptor.capture());
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                "workflow.action.send_email",
                "instance-1:send-node",
                "instance-1:send-node");
        assertThat(payloadCaptor.getValue())
                .containsEntry("campaignId", "campaign-1")
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("environmentId", "prod")
                .containsEntry("actorId", "user-1")
                .containsEntry("idempotencyKey", "instance-1:send-node")
                .containsEntry("workflowInstanceId", "instance-1")
                .containsEntry("workflowId", "workflow-1")
                .containsEntry("workflowVersion", 7)
                .containsEntry("nodeId", "send-node")
                .containsEntry("subscriberId", "subscriber-1")
                .containsEntry("confirmLaunch", true)
                .containsEntry("handoffBoundary", "CAMPAIGN_ORCHESTRATION")
                .containsEntry("requiresCampaignPreflight", true)
                .containsEntry("sendLifecycleOwner", "campaign-service")
                .containsEntry("traceId", "instance-1:send-node");
    }

    @Test
    void executeSkipsDuplicateHandoff() {
        WorkflowInstance instance = workflowInstance();
        WorkflowGraphDto.WorkflowNode node = sendNode(Map.of("campaignId", "campaign-1"));
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                "workflow.action.send_email",
                "instance-1:send-node",
                "instance-1:send-node"))
                .thenReturn(false);

        String nextNode = handler.execute(instance, node);

        assertThat(nextNode).isEqualTo("next-node");
        verify(eventPublisher, never()).publishAction(any(), any(), any(), any());
        verify(idempotencyService, never()).markProcessed(any(), any(), any(), any(), any());
        verify(idempotencyService, never()).releaseClaim(any(), any(), any(), any(), any());
    }

    @Test
    void executeMarksProcessedOnlyAfterCampaignHandoffPublishes() {
        WorkflowInstance instance = workflowInstance();
        WorkflowGraphDto.WorkflowNode node = sendNode(Map.of("campaignId", "campaign-1"));
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                "workflow.action.send_email",
                "instance-1:send-node",
                "instance-1:send-node"))
                .thenReturn(true);

        handler.execute(instance, node);

        InOrder inOrder = inOrder(idempotencyService, eventPublisher);
        inOrder.verify(idempotencyService).claimIfNew(
                "tenant-1",
                "workspace-1",
                "workflow.action.send_email",
                "instance-1:send-node",
                "instance-1:send-node");
        inOrder.verify(eventPublisher).publishAction(
                eq(AppConstants.TOPIC_SEND_REQUESTED),
                eq("tenant-1"),
                eq("instance-1:send-node"),
                any());
        inOrder.verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                "workflow.action.send_email",
                "instance-1:send-node",
                "instance-1:send-node");
        verify(idempotencyService, never()).releaseClaim(any(), any(), any(), any(), any());
    }

    @Test
    void executeReleasesClaimWhenCampaignHandoffPublishFails() {
        WorkflowInstance instance = workflowInstance();
        WorkflowGraphDto.WorkflowNode node = sendNode(Map.of("campaignId", "campaign-1"));
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                "workflow.action.send_email",
                "instance-1:send-node",
                "instance-1:send-node"))
                .thenReturn(true);
        doThrow(new IllegalStateException("kafka unavailable")).when(eventPublisher).publishAction(
                eq(AppConstants.TOPIC_SEND_REQUESTED),
                eq("tenant-1"),
                eq("instance-1:send-node"),
                any());

        assertThatThrownBy(() -> handler.execute(instance, node))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kafka unavailable");

        verify(idempotencyService).releaseClaim(
                "tenant-1",
                "workspace-1",
                "workflow.action.send_email",
                "instance-1:send-node",
                "instance-1:send-node");
        verify(idempotencyService, never()).markProcessed(any(), any(), any(), any(), any());
    }

    @Test
    void executeRequiresSubscriberBeforePublishing() {
        WorkflowInstance instance = workflowInstance();
        instance.setSubscriberId(null);

        assertThatThrownBy(() -> handler.execute(instance, sendNode(Map.of("campaignId", "campaign-1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subscriberId is required");

        verifyNoInteractions(idempotencyService, eventPublisher);
    }

    private WorkflowInstance workflowInstance() {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId("instance-1");
        instance.setTenantId("tenant-1");
        instance.setWorkspaceId("workspace-1");
        instance.setEnvironmentId("prod");
        instance.setOwnershipScope("WORKSPACE");
        instance.setWorkflowId("workflow-1");
        instance.setVersion(7);
        instance.setSubscriberId("subscriber-1");
        return instance;
    }

    private WorkflowGraphDto.WorkflowNode sendNode(Map<String, Object> configuration) {
        WorkflowGraphDto.WorkflowNode node = new WorkflowGraphDto.WorkflowNode();
        node.setId("send-node");
        node.setType("SEND_EMAIL");
        node.setConfiguration(configuration);
        node.setNextNodeId("next-node");
        node.setBranches(List.of());
        return node;
    }
}
