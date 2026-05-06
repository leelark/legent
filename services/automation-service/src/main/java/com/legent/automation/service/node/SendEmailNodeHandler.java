package com.legent.automation.service.node;

import com.legent.common.constant.AppConstants;
import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.automation.service.AutomationEventIdempotencyService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendEmailNodeHandler implements NodeHandler {

    private final WorkflowEventPublisher eventPublisher;
    private final AutomationEventIdempotencyService idempotencyService;

    @Override
    public String getType() {
        return "SEND_EMAIL";
    }

    @Override
    public String execute(WorkflowInstance instance, WorkflowGraphDto.WorkflowNode node) {
        Map<String, Object> configuration = node.getConfiguration() == null ? Map.of() : node.getConfiguration();
        String campaignId = (String) configuration.get("campaignId");
        if (campaignId == null || campaignId.isBlank()) {
            throw new IllegalArgumentException("SEND_EMAIL node requires a campaignId in configuration");
        }
        if (instance.getWorkspaceId() == null || instance.getWorkspaceId().isBlank()) {
            throw new IllegalStateException("workspaceId is required for SEND_EMAIL action");
        }

        log.debug("Executing SEND_EMAIL node for instance {}. target campaign: {}", instance.getId(), campaignId);

        String traceJobId = UUID.randomUUID().toString();
        String launchIdempotencyKey = instance.getId() + ":" + node.getId();
        if (!idempotencyService.registerIfNew(
                instance.getTenantId(),
                instance.getWorkspaceId(),
                "workflow.action.send_email",
                launchIdempotencyKey,
                launchIdempotencyKey
        )) {
            log.info("Skipping duplicate SEND_EMAIL action for instance={} node={}", instance.getId(), node.getId());
            return node.getNextNodeId();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", campaignId);
        payload.put("triggerSource", "AUTOMATION");
        payload.put("triggerReference", instance.getId());
        payload.put("idempotencyKey", launchIdempotencyKey);
        payload.put("instanceId", instance.getId());
        payload.put("workspaceId", instance.getWorkspaceId());
        payload.put("environmentId", instance.getEnvironmentId());
        payload.put("actorId", TenantContext.getUserId());
        payload.put("ownershipScope", instance.getOwnershipScope());

        // Emit campaign trigger request. Campaign service remains the send lifecycle owner.
        eventPublisher.publishAction(AppConstants.TOPIC_SEND_REQUESTED, instance.getTenantId(), traceJobId, payload);

        return node.getNextNodeId();
    }
}
