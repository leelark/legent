package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WaitUntilNodeHandlerTest {

    @Mock
    private Scheduler scheduler;

    private WaitUntilNodeHandler handler;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        TenantContext.setRequestId("request-1");
        TenantContext.setCorrelationId("correlation-1");
        handler = new WaitUntilNodeHandler(scheduler);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void executeSchedulesFutureWakeWithTenantWorkspaceScope() throws Exception {
        Instant fireTime = Instant.now().plus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        WorkflowInstance instance = workflowInstance();
        WorkflowGraphDto.WorkflowNode node = node(Map.of("at", fireTime.toString()));

        String nextNode = handler.execute(instance, node);

        assertThat(nextNode).isNull();
        assertThat(instance.getStatus()).isEqualTo("WAITING");
        ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(jobCaptor.capture(), triggerCaptor.capture());

        JobDetail job = jobCaptor.getValue();
        assertThat(job.getKey().getGroup()).isEqualTo("workflow-wait-until");
        assertThat(job.getJobDataMap())
                .containsEntry("instanceId", "instance-1")
                .containsEntry("nextNodeId", "next-node")
                .containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("environmentId", "prod")
                .containsEntry("requestId", "request-1")
                .containsEntry("correlationId", "correlation-1");
        assertThat(job.getJobDataMap().getString("wakeId"))
                .startsWith("instance-1:wait-node:");
        assertThat(triggerCaptor.getValue().getKey().getGroup()).isEqualTo("workflow-wait-until");
        assertThat(triggerCaptor.getValue().getStartTime().toInstant()).isEqualTo(fireTime);
    }

    @Test
    void executeContinuesImmediatelyWhenTimestampAlreadyPassed() {
        WorkflowInstance instance = workflowInstance();
        WorkflowGraphDto.WorkflowNode node = node(Map.of("at", Instant.now().minus(1, ChronoUnit.MINUTES).toString()));

        String nextNode = handler.execute(instance, node);

        assertThat(nextNode).isEqualTo("next-node");
        assertThat(instance.getStatus()).isEqualTo("RUNNING");
        verifyNoInteractions(scheduler);
    }

    @Test
    void executeRejectsMissingTimestamp() {
        assertThatThrownBy(() -> handler.execute(workflowInstance(), node(Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configuration.at or configuration.until");

        verifyNoInteractions(scheduler);
    }

    @Test
    void executeRejectsInvalidTimestamp() {
        assertThatThrownBy(() -> handler.execute(workflowInstance(), node(Map.of("at", "tomorrow"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-8601 instant");

        verifyNoInteractions(scheduler);
    }

    @Test
    void executeRejectsWaitsBeyondSevenDays() {
        assertThatThrownBy(() -> handler.execute(
                workflowInstance(),
                node(Map.of("at", Instant.now().plus(10081, ChronoUnit.MINUTES).toString()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10080 minutes");

        verifyNoInteractions(scheduler);
    }

    private WorkflowInstance workflowInstance() {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId("instance-1");
        instance.setTenantId("tenant-1");
        instance.setWorkspaceId("workspace-1");
        instance.setEnvironmentId("prod");
        instance.setWorkflowId("workflow-1");
        instance.setVersion(3);
        instance.setSubscriberId("subscriber-1");
        instance.setStatus("RUNNING");
        return instance;
    }

    private WorkflowGraphDto.WorkflowNode node(Map<String, Object> configuration) {
        WorkflowGraphDto.WorkflowNode node = new WorkflowGraphDto.WorkflowNode();
        node.setId("wait-node");
        node.setType("WAIT_UNTIL");
        node.setConfiguration(configuration);
        node.setNextNodeId("next-node");
        node.setBranches(List.of());
        return node;
    }
}
