package com.legent.deliverability.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import com.legent.deliverability.domain.SuppressionList;
import com.legent.deliverability.repository.SenderDomainRepository;
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
    private final SenderDomainRepository senderDomainRepository;
    private final ReputationEngine reputationEngine;

    @KafkaListener(topics = {AppConstants.TOPIC_EMAIL_BOUNCED, AppConstants.TOPIC_EMAIL_COMPLAINT}, groupId = AppConstants.GROUP_DELIVERABILITY)
    public void consumeDeliveryFailedEvents(EventEnvelope<?> event) {
        try {
            TenantContext.setTenantId(event.getTenantId());
            Map<String, Object> payload = toPayloadMap(event.getPayload());
            if (payload == null || payload.isEmpty()) {
                log.warn("Skipping feedback event {}: empty payload", event.getEventId());
                return;
            }
            
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

                // Penalize domain reputation if applicable - must resolve to sender_domains.id
                String reputationDomainId = resolveDomainId(event.getTenantId(), domainId, senderDomain);
                if (reputationDomainId != null) {
                    try {
                        reputationEngine.recordNegativeSignal(event.getTenantId(), reputationDomainId, reason);
                    } catch (Exception ex) {
                        log.warn("Reputation update skipped for tenant {} domain {}: {}",
                                event.getTenantId(), reputationDomainId, ex.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to process feedback loop event", e);
        } finally {
            TenantContext.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPayloadMap(Object payload) throws Exception {
        if (payload == null) {
            return null;
        }
        if (payload instanceof Map<?, ?> mapPayload) {
            return (Map<String, Object>) mapPayload;
        }
        if (payload instanceof String textPayload) {
            if (textPayload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(textPayload, new TypeReference<>() {});
        }
        return objectMapper.convertValue(payload, new TypeReference<>() {});
    }

    private String resolveDomainId(String tenantId, String domainId, String senderDomain) {
        if (domainId != null && !domainId.isBlank()) {
            return domainId;
        }
        if (senderDomain == null || senderDomain.isBlank()) {
            return null;
        }
        return senderDomainRepository
                .findByTenantIdAndDomainName(tenantId, senderDomain.trim().toLowerCase())
                .map(com.legent.deliverability.domain.SenderDomain::getId)
                .orElse(null);
    }
}
