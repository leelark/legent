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
import com.legent.deliverability.service.DeliverabilityEventIdempotencyService;
import com.legent.deliverability.service.ReputationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import org.springframework.messaging.handler.annotation.Header;

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
    private final DeliverabilityEventIdempotencyService idempotencyService;

    @KafkaListener(topics = {AppConstants.TOPIC_EMAIL_BOUNCED, AppConstants.TOPIC_EMAIL_COMPLAINT}, groupId = AppConstants.GROUP_DELIVERABILITY)
    public void consumeDeliveryFailedEvents(EventEnvelope<?> event,
                                            @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
        try {
            Map<String, Object> payload = toPayloadMap(event.getPayload());
            String workspaceId = resolveWorkspaceId(event, payload);
            String eventType = resolveCanonicalEventType(event.getEventType(), receivedTopic);
            if (!idempotencyService.registerIfNew(
                    event.getTenantId(),
                    workspaceId,
                    eventType,
                    event.getEventId(),
                    event.getIdempotencyKey())) {
                return;
            }
            TenantContext.setTenantId(event.getTenantId());
            TenantContext.setWorkspaceId(workspaceId);
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
            boolean isComplaint = AppConstants.TOPIC_EMAIL_COMPLAINT.equals(eventType);
            if (isHardBounce || isComplaint) {

                String reason = isComplaint ? "COMPLAINT" : "HARD_BOUNCE";

                // Add to suppression list if it doesn't exist
                if (suppressionRepository.findByTenantIdAndWorkspaceIdAndEmail(event.getTenantId(), workspaceId, email).isEmpty()) {
                    SuppressionList suppression = new SuppressionList();
                    suppression.setId(UUID.randomUUID().toString());
                    suppression.setTenantId(event.getTenantId());
                    suppression.setWorkspaceId(workspaceId);
                    suppression.setEmail(email);
                    suppression.setReason(reason);
                    suppression.setSource(event.getSource());
                    suppression.setOwnershipScope("WORKSPACE");
                    suppressionRepository.save(suppression);

                    log.info("Added {} to SuppressionList for tenant {} due to {}", email, event.getTenantId(), reason);
                }

                // Penalize domain reputation if applicable - must resolve to sender_domains.id
                String reputationDomainId = resolveDomainId(event.getTenantId(), workspaceId, domainId, senderDomain);
                if (reputationDomainId != null) {
                    try {
                        reputationEngine.recordNegativeSignal(event.getTenantId(), workspaceId, reputationDomainId, reason);
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

    private String resolveDomainId(String tenantId, String workspaceId, String domainId, String senderDomain) {
        if (domainId != null && !domainId.isBlank()) {
            return domainId;
        }
        if (senderDomain == null || senderDomain.isBlank()) {
            return null;
        }
        return senderDomainRepository
                .findByTenantIdAndWorkspaceIdAndDomainName(tenantId, workspaceId, senderDomain.trim().toLowerCase())
                .map(com.legent.deliverability.domain.SenderDomain::getId)
                .orElse(null);
    }

    private String resolveWorkspaceId(EventEnvelope<?> event, Map<String, Object> payload) {
        if (event.getWorkspaceId() != null && !event.getWorkspaceId().isBlank()) {
            return event.getWorkspaceId();
        }
        if (payload != null) {
            Object fromPayload = payload.get("workspaceId");
            if (fromPayload != null) {
                String parsed = String.valueOf(fromPayload).trim();
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }
        }
        throw new IllegalArgumentException("Missing workspaceId for feedback event " + event.getEventId());
    }

    private String resolveCanonicalEventType(String envelopeEventType, String receivedTopic) {
        String eventType = envelopeEventType != null && !envelopeEventType.isBlank()
                ? envelopeEventType.trim()
                : (receivedTopic == null ? null : receivedTopic.trim());
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Missing eventType for deliverability feedback event");
        }
        if (!AppConstants.TOPIC_EMAIL_BOUNCED.equals(eventType) && !AppConstants.TOPIC_EMAIL_COMPLAINT.equals(eventType)) {
            throw new IllegalArgumentException("Unsupported feedback event type: " + eventType);
        }
        if (receivedTopic != null && !receivedTopic.isBlank() && !eventType.equals(receivedTopic.trim())) {
            throw new IllegalArgumentException("Event type/topic mismatch: eventType=" + eventType + ", topic=" + receivedTopic);
        }
        return eventType;
    }
}
