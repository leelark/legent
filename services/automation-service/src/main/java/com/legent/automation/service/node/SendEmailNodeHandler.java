package com.legent.automation.service.node;

import com.legent.common.constant.AppConstants;
import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.automation.event.WorkflowEventPublisher;
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

        log.debug("Executing SEND_EMAIL node for instance {}. target campaign: {}", instance.getId(), campaignId);

        String traceJobId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriberId", instance.getSubscriberId());
        payload.put("campaignId", campaignId);
        payload.put("instanceId", instance.getId());

        // Emit to delivery-service directly, bypassing batching orchestrator
        eventPublisher.publishAction(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, instance.getTenantId(), traceJobId, payload);

        return node.getNextNodeId();
    }
}
