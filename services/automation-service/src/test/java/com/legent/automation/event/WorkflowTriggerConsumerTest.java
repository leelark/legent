package com.legent.automation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.service.AutomationEventIdempotencyService;
import com.legent.automation.service.WorkflowEngine;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTriggerConsumerTest {

    @Mock private WorkflowEngine workflowEngine;
    @Mock private AutomationEventIdempotencyService idempotencyService;

    private WorkflowTriggerConsumer consumer;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        consumer = new WorkflowTriggerConsumer(new ObjectMapper(), workflowEngine, idempotencyService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void consumeTriggerMarksProcessedOnlyAfterWorkflowStartSucceeds() {
        EventEnvelope<Object> event = triggerEvent();
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1")).thenReturn(true);
        doAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("tenant-1");
            assertThat(TenantContext.getWorkspaceId()).isEqualTo("workspace-1");
            assertThat(TenantContext.getEnvironmentId()).isEqualTo("production");
            assertThat(TenantContext.getUserId()).isEqualTo("user-1");
            assertThat(TenantContext.getRequestId()).isEqualTo("idem-1");
            assertThat(TenantContext.getCorrelationId()).isEqualTo("corr-1");
            return null;
        }).when(workflowEngine).startWorkflow(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow-1"),
                eq(3),
                eq("subscriber-1"),
                any(),
                eq("production"),
                eq("user-1"),
                eq("idem-1"),
                eq("corr-1"));

        consumer.consumeTrigger(event);

        InOrder inOrder = inOrder(idempotencyService, workflowEngine);
        inOrder.verify(idempotencyService).claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1");
        inOrder.verify(workflowEngine).startWorkflow(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow-1"),
                eq(3),
                eq("subscriber-1"),
                any(),
                eq("production"),
                eq("user-1"),
                eq("idem-1"),
                eq("corr-1"));
        inOrder.verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1");
        verify(idempotencyService, never()).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1");
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    @Test
    void consumeTriggerReleasesClaimAndRetryCanStartWorkflowAfterFailure() {
        EventEnvelope<Object> event = triggerEvent();
        RuntimeException failure = new IllegalStateException("database unavailable");
        AtomicInteger attempts = new AtomicInteger();
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1")).thenReturn(true, true);
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw failure;
            }
            return null;
        }).when(workflowEngine).startWorkflow(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow-1"),
                eq(3),
                eq("subscriber-1"),
                any(),
                eq("production"),
                eq("user-1"),
                eq("idem-1"),
                eq("corr-1"));

        assertThatThrownBy(() -> consumer.consumeTrigger(event)).isSameAs(failure);
        consumer.consumeTrigger(event);

        verify(workflowEngine, org.mockito.Mockito.times(2)).startWorkflow(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow-1"),
                eq(3),
                eq("subscriber-1"),
                any(),
                eq("production"),
                eq("user-1"),
                eq("idem-1"),
                eq("corr-1"));
        verify(idempotencyService).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1");
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1");
    }

    @Test
    void consumeTriggerSkipsDuplicateClaimWithoutStartingWorkflow() {
        EventEnvelope<Object> event = triggerEvent();
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1")).thenReturn(false);

        consumer.consumeTrigger(event);

        verify(idempotencyService, never()).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1");
        verify(idempotencyService, never()).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                "evt-1",
                "idem-1");
        verifyNoInteractions(workflowEngine);
    }

    @Test
    void consumeTriggerDropsMalformedPayloadBeforeClaiming() {
        EventEnvelope<Object> event = triggerEvent("{not-json");

        consumer.consumeTrigger(event);

        verifyNoInteractions(idempotencyService, workflowEngine);
    }

    @Test
    void triggerConsumerStartContractIsTransactionalAndSynchronous() throws NoSuchMethodException {
        Method consume = WorkflowTriggerConsumer.class.getMethod("consumeTrigger", EventEnvelope.class);
        assertThat(consume.isAnnotationPresent(Transactional.class)).isTrue();

        Method start = WorkflowEngine.class.getMethod(
                "startWorkflow",
                String.class,
                String.class,
                String.class,
                Integer.class,
                String.class,
                Map.class,
                String.class,
                String.class,
                String.class,
                String.class);
        assertThat(start.isAnnotationPresent(Async.class)).isFalse();
    }

    private EventEnvelope<Object> triggerEvent() {
        return triggerEvent(Map.of(
                "workflowId", "workflow-1",
                "version", 3,
                "subscriberId", "subscriber-1",
                "context", Map.of("source", "unit-test")));
    }

    private EventEnvelope<Object> triggerEvent(Object payload) {
        return EventEnvelope.<Object>builder()
                .eventId("evt-1")
                .eventType(AppConstants.TOPIC_WORKFLOW_TRIGGER)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .environmentId("production")
                .actorId("user-1")
                .correlationId("corr-1")
                .idempotencyKey("idem-1")
                .payload(payload)
                .build();
    }
}
