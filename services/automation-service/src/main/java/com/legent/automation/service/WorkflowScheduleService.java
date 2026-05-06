package com.legent.automation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.domain.Workflow;
import com.legent.automation.domain.WorkflowSchedule;
import com.legent.automation.repository.WorkflowRepository;
import com.legent.automation.repository.WorkflowScheduleRepository;
import com.legent.automation.service.node.WorkflowScheduleTriggerJob;
import com.legent.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@Service
@RequiredArgsConstructor
public class WorkflowScheduleService {

    private final WorkflowScheduleRepository workflowScheduleRepository;
    private final WorkflowRepository workflowRepository;
    private final Scheduler scheduler;
    private final ObjectMapper objectMapper;

    public List<WorkflowSchedule> listSchedules(String workflowId) {
        return workflowScheduleRepository.findByTenantIdAndWorkspaceIdAndWorkflowIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                requireTenant(), requireWorkspace(), workflowId);
    }

    @Transactional
    public WorkflowSchedule createSchedule(String workflowId, Map<String, Object> request) {
        Workflow workflow = findWorkflow(workflowId);
        String cron = required(asString(request.get("cronExpression")), "cronExpression");
        String timezone = defaultString(asString(request.get("timezone")), "UTC");
        String status = defaultString(asString(request.get("status")), "ACTIVE");
        Integer version = asInteger(request.get("version"));

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setTenantId(workflow.getTenantId());
        schedule.setWorkspaceId(workflow.getWorkspaceId());
        schedule.setWorkflowId(workflowId);
        schedule.setScheduleType("CRON");
        schedule.setCronExpression(cron);
        schedule.setTimezone(timezone);
        schedule.setStatus(status);
        schedule.setCreatedBy(defaultString(TenantContext.getUserId(), "system"));
        schedule.setMetadata(toJson(request.get("metadata")));
        WorkflowSchedule saved = workflowScheduleRepository.save(schedule);
        if ("ACTIVE".equalsIgnoreCase(saved.getStatus())) {
            upsertQuartzSchedule(saved, version != null ? version : workflow.getActiveDefinitionVersion());
        }
        return saved;
    }

    @Transactional
    public WorkflowSchedule updateSchedule(String workflowId, String scheduleId, Map<String, Object> request) {
        WorkflowSchedule schedule = findSchedule(scheduleId);
        if (!workflowId.equals(schedule.getWorkflowId())) {
            throw new IllegalArgumentException("Schedule does not belong to workflow");
        }
        if (request.containsKey("cronExpression")) {
            schedule.setCronExpression(required(asString(request.get("cronExpression")), "cronExpression"));
        }
        if (request.containsKey("timezone")) {
            schedule.setTimezone(defaultString(asString(request.get("timezone")), "UTC"));
        }
        if (request.containsKey("status")) {
            schedule.setStatus(defaultString(asString(request.get("status")), "ACTIVE"));
        }
        if (request.containsKey("metadata")) {
            schedule.setMetadata(toJson(request.get("metadata")));
        }
        WorkflowSchedule saved = workflowScheduleRepository.save(schedule);
        Integer version = asInteger(request.get("version"));
        if ("ACTIVE".equalsIgnoreCase(saved.getStatus())) {
            Workflow workflow = findWorkflow(workflowId);
            upsertQuartzSchedule(saved, version != null ? version : workflow.getActiveDefinitionVersion());
        } else {
            unschedule(saved.getId());
        }
        return saved;
    }

    @Transactional
    public void deleteSchedule(String workflowId, String scheduleId) {
        WorkflowSchedule schedule = findSchedule(scheduleId);
        if (!workflowId.equals(schedule.getWorkflowId())) {
            throw new IllegalArgumentException("Schedule does not belong to workflow");
        }
        schedule.softDelete();
        schedule.setStatus("DELETED");
        workflowScheduleRepository.save(schedule);
        unschedule(scheduleId);
    }

    private void upsertQuartzSchedule(WorkflowSchedule schedule, Integer version) {
        try {
            JobKey jobKey = new JobKey(schedule.getId(), "workflow-schedules");
            TriggerKey triggerKey = new TriggerKey(schedule.getId() + "-trigger", "workflow-schedules");

            if (scheduler.checkExists(triggerKey)) {
                scheduler.unscheduleJob(triggerKey);
            }
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }

            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("scheduleId", schedule.getId());
            jobDataMap.put("tenantId", schedule.getTenantId());
            jobDataMap.put("workspaceId", schedule.getWorkspaceId());
            jobDataMap.put("workflowId", schedule.getWorkflowId());
            if (version != null) {
                jobDataMap.put("version", version);
            }

            JobDetail jobDetail = JobBuilder.newJob(WorkflowScheduleTriggerJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(jobDataMap)
                    .build();

            CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(schedule.getCronExpression())
                    .inTimeZone(TimeZone.getTimeZone(schedule.getTimezone()))
                    .withMisfireHandlingInstructionDoNothing();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobDetail)
                    .withSchedule(cronScheduleBuilder)
                    .build();

            Date nextFireTime = trigger.getNextFireTime();
            schedule.setNextRunAt(nextFireTime == null ? null : nextFireTime.toInstant());
            schedule.setLastRunAt(Instant.now());
            workflowScheduleRepository.save(schedule);

            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to schedule workflow", e);
        }
    }

    private void unschedule(String scheduleId) {
        try {
            TriggerKey triggerKey = new TriggerKey(scheduleId + "-trigger", "workflow-schedules");
            JobKey jobKey = new JobKey(scheduleId, "workflow-schedules");
            if (scheduler.checkExists(triggerKey)) {
                scheduler.unscheduleJob(triggerKey);
            }
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to remove workflow schedule", e);
        }
    }

    private Workflow findWorkflow(String workflowId) {
        return workflowRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(workflowId, requireTenant(), requireWorkspace())
                .orElseThrow(() -> new EntityNotFoundException("Workflow not found"));
    }

    private WorkflowSchedule findSchedule(String scheduleId) {
        return workflowScheduleRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(scheduleId, requireTenant(), requireWorkspace())
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found"));
    }

    private String requireTenant() {
        return TenantContext.requireTenantId();
    }

    private String requireWorkspace() {
        return TenantContext.requireWorkspaceId();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String defaultString(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String toJson(Object metadata) {
        if (metadata == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new IllegalArgumentException("metadata must be serializable JSON", e);
        }
    }
}
