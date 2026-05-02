package com.legent.deliverability.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import com.legent.deliverability.domain.SuppressionList;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.deliverability.service.ReputationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "legent.deliverability.feedback-loop.enabled", havingValue = "true", matchIfMissing = true)
public class FeedbackLoopConsumer {

    private final ObjectMapper objectMapper;
    private final SuppressionListRepository suppressionRepository;
    private final ReputationEngine reputationEngine;

    @KafkaListener(topics = {AppConstants.TOPIC_EMAIL_BOUNCED, AppConstants.TOPIC_EMAIL_COMPLAINT}, groupId = AppConstants.GROUP_DELIVERABILITY)
    public void consumeDeliveryFailedEvents(EventEnvelope<String> event) {
        try {
            TenantContext.setTenantId(event.getTenantId());
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
            
            String email = (String) payload.get("email");
            String bounceType = (String) payload.get("bounceType");
            if (bounceType == null) {
                bounceType = (String) payload.get("type");
            }
            String domainId = (String) payload.get("domainId");
            String senderDomain = (String) payload.get("senderDomain");

            if (email == null || email.isBlank()) return;

            // Handle Hard Bounces & Complaints
            boolean isHardBounce = bounceType != null && bounceType.contains("HARD");
            boolean isComplaint = AppConstants.TOPIC_EMAIL_COMPLAINT.equals(event.getEventType());
            if (isHardBounce || isComplaint) {

                String reason = isComplaint ? "COMPLAINT" : "HARD_BOUNCE";

                // Add to suppression list if it doesn't exist
                if (suppressionRepository.findByTenantIdAndEmail(event.getTenantId(), email).isEmpty()) {
                    SuppressionList suppression = new SuppressionList();
                    suppression.setId(UUID.randomUUID().toString());
                    suppression.setTenantId(event.getTenantId());
                    suppression.setEmail(email);
                    suppression.setReason(reason);
                    suppression.setSource(event.getSource());
                    suppressionRepository.save(suppression);

                    log.info("Added {} to SuppressionList for tenant {} due to {}", email, event.getTenantId(), reason);
                }

                // Penalize domain reputation if applicable - use senderDomain or domainId
                String reputationDomain = domainId != null ? domainId : senderDomain;
                if (reputationDomain != null && !reputationDomain.isBlank()) {
                    reputationEngine.recordNegativeSignal(event.getTenantId(), reputationDomain, reason);
                }
            }

        } catch (Exception e) {
            log.error("Failed to process feedback loop event", e);
        } finally {
            TenantContext.clear();
        }
    }
}
