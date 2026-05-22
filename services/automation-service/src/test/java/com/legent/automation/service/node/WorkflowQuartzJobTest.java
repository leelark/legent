package com.legent.automation.service.node;

import com.legent.automation.service.WorkflowEngine;
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowQuartzJobTest {

    @Test
    void executeInternalPassesStoredTenantAndWorkspaceScopeToEngine() throws Exception {
        WorkflowEngine workflowEngine = mock(WorkflowEngine.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);
        JobDetail jobDetail = JobBuilder.newJob(WorkflowQuartzJob.class)
                .withIdentity("wake-1", "workflow-delay")
                .usingJobData("instanceId", "instance-1")
                .usingJobData("nextNodeId", "node-2")
                .usingJobData("wakeId", "wake-1")
                .usingJobData("tenantId", "tenant-1")
                .usingJobData("workspaceId", "workspace-1")
                .build();
        when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);
        when(applicationContext.getBean(WorkflowEngine.class)).thenReturn(workflowEngine);

        new WorkflowQuartzJob(applicationContext).executeInternal(jobExecutionContext);

        verify(workflowEngine).resumeInstance("instance-1", "node-2", "wake-1", "tenant-1", "workspace-1");
    }
}
