package com.legent.deliverability.event;

import com.fasterxml.jackson.core.JsonProcessingException;
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
        String tenantId = event == null ? null : event.getTenantId();
        String workspaceId = null;
        String eventType = null;
        String eventId = event == null ? null : event.getEventId();
        String idempotencyKey = event == null ? null : event.getIdempotencyKey();
        boolean claimed = false;
        try {
            if (event == null) {
                log.warn("Dropping feedback loop event: null envelope");
                return;
            }
            Map<String, Object> payload = toPayloadMap(event.getPayload());
            workspaceId = resolveWorkspaceId(event, payload);
            eventType = resolveCanonicalEventType(event.getEventType(), receivedTopic);
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

            if (email == null || email.isBlank()) {
                log.warn("Skipping feedback event {}: missing email", event.getEventId());
                return;
            }

            if (!hasClaimKey(eventId, idempotencyKey)) {
                log.warn("Dropping feedback event {}: missing idempotency key", event.getEventId());
                return;
            }

            claimed = idempotencyService.claimIfNew(
                    tenantId,
                    workspaceId,
                    eventType,
                    eventId,
                    idempotencyKey);
            if (!claimed) {
                return;
            }

            TenantContext.setTenantId(tenantId);
            TenantContext.setWorkspaceId(workspaceId);

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
                    reputationEngine.recordNegativeSignal(tenantId, workspaceId, reputationDomainId, reason);
                }
            }

            idempotencyService.markProcessed(tenantId, workspaceId, eventType, eventId, idempotencyKey);

        } catch (JsonProcessingException e) {
            log.warn("Dropping malformed feedback loop event. eventId={}", event == null ? "unknown" : event.getEventId(), e);
        } catch (InvalidFeedbackEventException e) {
            log.warn("Dropping invalid feedback loop event. eventId={}, reason={}",
                    event == null ? "unknown" : event.getEventId(), e.getMessage());
        } catch (Exception e) {
            if (claimed) {
                releaseClaim(tenantId, workspaceId, eventType, eventId, idempotencyKey, e);
            }
            log.error("Failed to process feedback loop event", e);
            throw new IllegalStateException("Failed to process feedback loop event", e);
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

    private void releaseClaim(String tenantId,
                              String workspaceId,
                              String eventType,
                              String eventId,
                              String idempotencyKey,
                              Exception processingFailure) {
        try {
            idempotencyService.releaseClaim(tenantId, workspaceId, eventType, eventId, idempotencyKey);
        } catch (Exception releaseFailure) {
            processingFailure.addSuppressed(releaseFailure);
            log.error("Failed to release feedback-loop idempotency claim eventId={}", eventId, releaseFailure);
        }
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
        throw new InvalidFeedbackEventException("Missing workspaceId for feedback event " + event.getEventId());
    }

    private String resolveCanonicalEventType(String envelopeEventType, String receivedTopic) {
        String eventType = envelopeEventType != null && !envelopeEventType.isBlank()
                ? envelopeEventType.trim()
                : (receivedTopic == null ? null : receivedTopic.trim());
        if (eventType == null || eventType.isBlank()) {
            throw new InvalidFeedbackEventException("Missing eventType for deliverability feedback event");
        }
        if (!AppConstants.TOPIC_EMAIL_BOUNCED.equals(eventType) && !AppConstants.TOPIC_EMAIL_COMPLAINT.equals(eventType)) {
            throw new InvalidFeedbackEventException("Unsupported feedback event type: " + eventType);
        }
        if (receivedTopic != null && !receivedTopic.isBlank() && !eventType.equals(receivedTopic.trim())) {
            throw new InvalidFeedbackEventException("Event type/topic mismatch: eventType=" + eventType + ", topic=" + receivedTopic);
        }
        return eventType;
    }

    private boolean hasClaimKey(String eventId, String idempotencyKey) {
        return eventId != null && !eventId.isBlank()
                || idempotencyKey != null && !idempotencyKey.isBlank();
    }

    private static class InvalidFeedbackEventException extends RuntimeException {
        private InvalidFeedbackEventException(String message) {
            super(message);
        }
    }
}
