package com.legent.automation.service.node;

import com.legent.automation.service.WorkflowEngine;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Component
public class WorkflowQuartzJob extends QuartzJobBean {

    // Spring injects context into QuartzJobBean automatically if configured properly, but fallback via ApplicationContext lookup works well
    private final ApplicationContext applicationContext;

    public WorkflowQuartzJob(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    protected void executeInternal(@org.springframework.lang.NonNull JobExecutionContext context) throws JobExecutionException {
        String instanceId = context.getJobDetail().getJobDataMap().getString("instanceId");
        String nextNodeId = context.getJobDetail().getJobDataMap().getString("nextNodeId");
        String wakeId = context.getJobDetail().getJobDataMap().getString("wakeId");
        
        WorkflowEngine engine = applicationContext.getBean(WorkflowEngine.class);
        
        // Resume engine execution asynchronously
        engine.resumeInstance(instanceId, nextNodeId, wakeId);
    }
}
