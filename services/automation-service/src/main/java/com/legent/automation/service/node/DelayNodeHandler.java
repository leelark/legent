package com.legent.automation.service.node;

import java.time.Instant;

import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelayNodeHandler implements NodeHandler {

    private final Scheduler scheduler;

    @Override
    public String getType() {
        return "DELAY";
    }

    @Override
    public String execute(WorkflowInstance instance, WorkflowGraphDto.WorkflowNode node) {
        int waitMinutes = resolveWaitMinutes(node);

        Instant fireTime = Instant.now().plus(waitMinutes, ChronoUnit.MINUTES);
        String wakeId = instance.getId() + ":" + node.getId() + ":" + fireTime.toEpochMilli();

        try {
            JobDetail job = JobBuilder.newJob(WorkflowQuartzJob.class)
                    .withIdentity(wakeId, "workflow-delay")
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
                    .withIdentity(wakeId + "-trigger", "workflow-delay")
                    .startAt(Date.from(fireTime))
                    .build();

            scheduler.scheduleJob(job, trigger);
            
            instance.setStatus("WAITING");
            log.debug("Instance {} suspended. Scheduled wake up at {}", instance.getId(), fireTime);
            
        } catch (SchedulerException e) {
            log.error("Failed to schedule Quartz Job for delay step", e);
            throw new RuntimeException("Quartz scheduling error", e);
        }

        // Return NULL to halt the synchronous engine execution loop.
        return null; 
    }

    private int resolveWaitMinutes(WorkflowGraphDto.WorkflowNode node) {
        if (node.getConfiguration() == null) {
            return 60;
        }

        Object configuredMinutes = node.getConfiguration().get("minutes");
        if (configuredMinutes == null) {
            return 60;
        }

        if (configuredMinutes instanceof Number numberValue) {
            return Math.max(1, numberValue.intValue());
        }

        if (configuredMinutes instanceof String stringValue) {
            try {
                return Math.max(1, Integer.parseInt(stringValue.trim()));
            } catch (NumberFormatException ex) {
                log.warn("Invalid delay minutes '{}', defaulting to 60", stringValue);
            }
        }

        return 60;
    }
}
