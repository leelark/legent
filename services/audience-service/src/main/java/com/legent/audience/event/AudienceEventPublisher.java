package com.legent.audience.event;

import java.util.Map;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

/**
 * Event publisher for audience-related events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceEventPublisher {

    private final EventPublisher eventPublisher;

    public void publishConsentUpdated(String tenantId, String subscriberId, String consentType, boolean given) {
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_SUBSCRIBER_UPDATED,
                tenantId,
                "audience-service",
                Map.of(
                        "eventType", "CONSENT_UPDATED",
                        "subscriberId", subscriberId,
                        "consentType", consentType,
                        "consentGiven", given
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_SUBSCRIBER_UPDATED, envelope);
        log.debug("Published consent updated event for subscriber {}", subscriberId);
    }

    public void publishConsentWithdrawn(String tenantId, String subscriberId, String consentType) {
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_SUBSCRIBER_UPDATED,
                tenantId,
                "audience-service",
                Map.of(
                        "eventType", "CONSENT_WITHDRAWN",
                        "subscriberId", subscriberId,
                        "consentType", consentType
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_SUBSCRIBER_UPDATED, envelope);
        log.debug("Published consent withdrawn event for subscriber {}", subscriberId);
    }

    public void publishDoubleOptInConfirmed(String tenantId, String subscriberId, String email) {
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_SUBSCRIBER_UPDATED,
                tenantId,
                "audience-service",
                Map.of(
                        "eventType", "DOUBLE_OPT_IN_CONFIRMED",
                        "subscriberId", subscriberId,
                        "email", email
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_SUBSCRIBER_UPDATED, envelope);
        log.debug("Published double opt-in confirmed event for subscriber {}", subscriberId);
    }

    /**
     * AUDIT-019: Publish double opt-in requested event to trigger email sending.
     * The delivery-service consumes this event to send the confirmation email.
     */
    public void publishDoubleOptInRequested(String tenantId, String subscriberId, String email, String rawToken) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "DOUBLE_OPT_IN_REQUESTED");
        payload.put("subscriberId", subscriberId);
        payload.put("email", email);
        payload.put("token", rawToken);
        payload.put("templateId", "double-opt-in-confirmation");
        String workspaceId = TenantContext.getWorkspaceId();
        if (workspaceId != null && !workspaceId.isBlank()) {
            payload.put("workspaceId", workspaceId);
        }
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                tenantId,
                "audience-service",
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope);
        log.debug("Published double opt-in requested event for subscriber {}", subscriberId);
    }
}
