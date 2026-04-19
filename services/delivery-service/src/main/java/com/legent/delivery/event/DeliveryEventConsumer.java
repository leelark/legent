package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.delivery.service.DeliveryOrchestrationService;
import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventConsumer {

    private final DeliveryOrchestrationService orchestrationService;

    @KafkaListener(topics = AppConstants.TOPIC_EMAIL_SEND_REQUESTED, groupId = AppConstants.GROUP_DELIVERY, concurrency = "5")
    public void handleSendRequest(EventEnvelope<Map<String, Object>> event) {
        try {
            TenantContext.setTenantId(event.getTenantId());
            orchestrationService.processSendRequest(event.getPayload(), event.getTenantId(), event.getEventId());
        } catch (Exception e) {
            log.error("Error processing text send request {}", event.getEventId(), e);
        } finally {
            TenantContext.clear();
        }
    }
}
