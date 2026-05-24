package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitUntilNodeHandler implements NodeHandler {

    private static final Duration MAX_WAIT_UNTIL_FUTURE = Duration.ofMinutes(10080);

    private final Scheduler scheduler;

    @Override
    public String getType() {
        return "WAIT_UNTIL";
    }

    @Override
    public String execute(WorkflowInstance instance, WorkflowGraphDto.WorkflowNode node) {
        Instant fireTime = resolveFireTime(node);
        Instant now = Instant.now();
        if (!fireTime.isAfter(now)) {
            log.debug("WAIT_UNTIL node {} for instance {} is already due at {}", node.getId(), instance.getId(), fireTime);
            return node.getNextNodeId();
        }
        if (fireTime.isAfter(now.plus(MAX_WAIT_UNTIL_FUTURE))) {
            throw new IllegalArgumentException("WAIT_UNTIL node cannot schedule more than 10080 minutes in the future");
        }

        String wakeId = instance.getId() + ":" + node.getId() + ":" + fireTime.toEpochMilli();
        try {
            JobDetail job = JobBuilder.newJob(WorkflowQuartzJob.class)
                    .withIdentity(wakeId, "workflow-wait-until")
                    .usingJobData("instanceId", instance.getId())
                    .usingJobData("nextNodeId", node.getNextNodeId())
                    .usingJobData("tenantId", instance.getTenantId())
                    .usingJobData("workspaceId", instance.getWorkspaceId())
                    .usingJobData("environmentId", instance.getEnvironmentId())
                    .usingJobData("requestId", TenantContext.getRequestId())
                    .usingJobData("correlationId", TenantContext.getCorrelationId())
                    .usingJobData("wakeId", wakeId)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(wakeId + "-trigger", "workflow-wait-until")
                    .startAt(Date.from(fireTime))
                    .build();

            scheduler.scheduleJob(job, trigger);
            instance.setStatus("WAITING");
            log.debug("Instance {} suspended until {}", instance.getId(), fireTime);
        } catch (SchedulerException e) {
            log.error("Failed to schedule Quartz Job for WAIT_UNTIL step", e);
            throw new RuntimeException("Quartz scheduling error", e);
        }

        return null;
    }

    private Instant resolveFireTime(WorkflowGraphDto.WorkflowNode node) {
        Map<String, Object> configuration = node.getConfiguration() == null ? Map.of() : node.getConfiguration();
        Object value = configuration.get("at");
        if (value == null) {
            value = configuration.get("until");
        }
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException("WAIT_UNTIL node requires configuration.at or configuration.until");
        }
        try {
            return Instant.parse(String.valueOf(value).trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("WAIT_UNTIL node requires an ISO-8601 instant", ex);
        }
    }
}
