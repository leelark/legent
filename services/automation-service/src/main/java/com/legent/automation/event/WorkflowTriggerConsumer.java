package com.legent.automation.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.automation.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTriggerConsumer {

    private final ObjectMapper objectMapper;
    private final WorkflowEngine workflowEngine;

    @KafkaListener(topics = AppConstants.TOPIC_WORKFLOW_TRIGGER, groupId = AppConstants.GROUP_AUTOMATION)
    public void consumeTrigger(EventEnvelope<String> event) {
        try {
            com.legent.security.TenantContext.setTenantId(event.getTenantId());
            
            // Expected JSON payload format: 
            // { "workflowId": "uuid", "version": 1, "subscriberId": "uuid", "context": {} }
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
            
            String workflowId = (String) payload.get("workflowId");
            Integer version = (Integer) payload.get("version");
            String subscriberId = (String) payload.get("subscriberId");
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) payload.get("context");

            workflowEngine.startWorkflow(event.getTenantId(), workflowId, version, subscriberId, context);

        } catch (Exception e) {
            log.error("Failed to parse or trigger workflow execution", e);
        } finally {
            com.legent.security.TenantContext.clear();
        }
    }
}
