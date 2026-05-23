package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowSchedule;
import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.automation.repository.WorkflowScheduleRepository;
import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class WorkflowScheduleTriggerJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduleTriggerJob.class);
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final ApplicationContext applicationContext;

    public WorkflowScheduleTriggerJob(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    protected void executeInternal(@org.springframework.lang.NonNull JobExecutionContext context) throws JobExecutionException {
        TenantContext.clear();
        ScheduleJobData jobData = validatedJobData(context);
        if (jobData == null) {
            return;
        }

        WorkflowScheduleRepository scheduleRepository = applicationContext.getBean(WorkflowScheduleRepository.class);
        Optional<WorkflowSchedule> schedule = scheduleRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                jobData.scheduleId(), jobData.tenantId(), jobData.workspaceId());
        if (schedule.isEmpty() || !isActiveMatchingSchedule(schedule.get(), jobData)) {
            log.warn("Skipping stale workflow schedule trigger job. scheduleId={} tenantId={} workspaceId={}",
                    jobData.scheduleId(), jobData.tenantId(), jobData.workspaceId());
            return;
        }

        TenantContext.setTenantId(jobData.tenantId());
        TenantContext.setWorkspaceId(jobData.workspaceId());
        TenantContext.setRequestId(jobData.requestId());
        TenantContext.setCorrelationId(jobData.requestId());
        try {
            WorkflowEventPublisher eventPublisher = applicationContext.getBean(WorkflowEventPublisher.class);
            Map<String, Object> payload = new HashMap<>();
            payload.put("workflowId", jobData.workflowId());
            payload.put("version", jobData.version());
            payload.put("subscriberId", "schedule:" + jobData.scheduleId());
            payload.put("workspaceId", jobData.workspaceId());
            payload.put("idempotencyKey", jobData.requestId());
            payload.put("context", Map.of(
                    "triggerSource", "SCHEDULED",
                    "scheduleId", jobData.scheduleId(),
                    "scheduledAt", jobData.scheduledAt().toString()
            ));
            eventPublisher.publishAction(AppConstants.TOPIC_WORKFLOW_TRIGGER, jobData.tenantId(), jobData.scheduleId(), payload);
        } finally {
            TenantContext.clear();
        }
    }

    private ScheduleJobData validatedJobData(JobExecutionContext context) {
        JobDetail jobDetail = context.getJobDetail();
        if (jobDetail == null || jobDetail.getJobDataMap() == null) {
            log.warn("Skipping workflow schedule trigger job with missing Quartz job data");
            return null;
        }
        JobDataMap dataMap = jobDetail.getJobDataMap();
        String tenantId = requiredString(dataMap.get("tenantId"), "tenantId");
        String workspaceId = requiredString(dataMap.get("workspaceId"), "workspaceId");
        String workflowId = requiredString(dataMap.get("workflowId"), "workflowId");
        String scheduleId = requiredString(dataMap.get("scheduleId"), "scheduleId");
        Integer version = optionalPositiveInteger(dataMap.get("version"), "version");
        Date scheduledFireTime = context.getScheduledFireTime();
        if (scheduledFireTime == null) {
            log.warn("Skipping workflow schedule trigger job with missing scheduled fire time");
            return null;
        }
        if (tenantId == null || workspaceId == null || workflowId == null || scheduleId == null
                || (dataMap.containsKey("version") && version == null)) {
            return null;
        }
        Instant scheduledAt = scheduledFireTime.toInstant();
        return new ScheduleJobData(
                tenantId,
                workspaceId,
                workflowId,
                scheduleId,
                version,
                scheduledAt,
                scheduleId + ":" + scheduledAt.toEpochMilli());
    }

    private boolean isActiveMatchingSchedule(WorkflowSchedule schedule, ScheduleJobData jobData) {
        return jobData.tenantId().equals(schedule.getTenantId())
                && jobData.workspaceId().equals(schedule.getWorkspaceId())
                && jobData.workflowId().equals(schedule.getWorkflowId())
                && schedule.getStatus() != null
                && ACTIVE_STATUS.equalsIgnoreCase(schedule.getStatus().trim());
    }

    private String requiredString(Object value, String fieldName) {
        if (value == null) {
            log.warn("Skipping workflow schedule trigger job with missing {}", fieldName);
            return null;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty()) {
            log.warn("Skipping workflow schedule trigger job with blank {}", fieldName);
            return null;
        }
        return normalized;
    }

    private Integer optionalPositiveInteger(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        Integer parsed = parseInteger(value);
        if (parsed == null || parsed <= 0) {
            log.warn("Skipping workflow schedule trigger job with invalid {}", fieldName);
            return null;
        }
        return parsed;
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ScheduleJobData(
            String tenantId,
            String workspaceId,
            String workflowId,
            String scheduleId,
            Integer version,
            Instant scheduledAt,
            String requestId) {
    }
}
