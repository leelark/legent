package com.legent.automation.service.node;

import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class WorkflowScheduleTriggerJob extends QuartzJobBean {

    private final ApplicationContext applicationContext;

    public WorkflowScheduleTriggerJob(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    protected void executeInternal(@org.springframework.lang.NonNull JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String tenantId = dataMap.getString("tenantId");
        String workspaceId = dataMap.getString("workspaceId");
        String workflowId = dataMap.getString("workflowId");
        String scheduleId = dataMap.getString("scheduleId");
        Integer version = dataMap.containsKey("version") ? dataMap.getInt("version") : null;

        String requestId = scheduleId + ":" + context.getScheduledFireTime().toInstant().toEpochMilli();
        TenantContext.setTenantId(tenantId);
        TenantContext.setWorkspaceId(workspaceId);
        TenantContext.setRequestId(requestId);
        TenantContext.setCorrelationId(requestId);
        try {
            WorkflowEventPublisher eventPublisher = applicationContext.getBean(WorkflowEventPublisher.class);
            Map<String, Object> payload = new HashMap<>();
            payload.put("workflowId", workflowId);
            payload.put("version", version);
            payload.put("subscriberId", "schedule:" + scheduleId);
            payload.put("workspaceId", workspaceId);
            payload.put("idempotencyKey", requestId);
            payload.put("context", Map.of(
                    "triggerSource", "SCHEDULED",
                    "scheduleId", scheduleId,
                    "scheduledAt", Instant.now().toString()
            ));
            eventPublisher.publishAction(AppConstants.TOPIC_WORKFLOW_TRIGGER, tenantId, scheduleId, payload);
        } finally {
            TenantContext.clear();
        }
    }
}
