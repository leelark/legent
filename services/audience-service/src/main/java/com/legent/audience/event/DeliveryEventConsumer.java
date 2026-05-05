package com.legent.audience.event;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.domain.Suppression;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.audience.repository.SuppressionRepository;
import com.legent.audience.service.AudienceEventIdempotencyService;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventConsumer {

    private final SubscriberRepository subscriberRepository;
    private final SuppressionRepository suppressionRepository;
    private final AudienceEventIdempotencyService idempotencyService;

    @Transactional
    @KafkaListener(topics = {
            AppConstants.TOPIC_EMAIL_BOUNCED,
            AppConstants.TOPIC_EMAIL_UNSUBSCRIBED,
            AppConstants.TOPIC_EMAIL_COMPLAINT
    }, groupId = "audience-delivery-group")
    public void handleDeliveryEvent(EventEnvelope<Map<String, Object>> envelope) {
        try {
            String tenantId = envelope.getTenantId();
            String workspaceId = (envelope.getWorkspaceId() == null || envelope.getWorkspaceId().isBlank())
                    ? "workspace-default"
                    : envelope.getWorkspaceId();
            String eventType = envelope.getEventType();
            Map<String, Object> payload = envelope.getPayload();
            String email = payload != null && payload.get("email") != null ? String.valueOf(payload.get("email")).toLowerCase().trim() : null;

            log.info("Processing delivery event: type={}, email={}, tenantId={}, workspaceId={}", eventType, email, tenantId, workspaceId);

            if (email == null) return;

            if (!idempotencyService.registerIfNew(tenantId, workspaceId, eventType, envelope.getEventId(), envelope.getIdempotencyKey())) {
                return;
            }

            if (AppConstants.TOPIC_EMAIL_BOUNCED.equalsIgnoreCase(eventType)) {
                String reason = payload != null && payload.get("reason") != null ? String.valueOf(payload.get("reason")) : null;
                handleBounce(tenantId, workspaceId, email, reason);
            } else if (AppConstants.TOPIC_EMAIL_UNSUBSCRIBED.equalsIgnoreCase(eventType)) {
                String reason = payload != null && payload.get("reason") != null ? String.valueOf(payload.get("reason")) : null;
                handleUnsubscribe(tenantId, workspaceId, email, reason);
            } else if (AppConstants.TOPIC_EMAIL_COMPLAINT.equalsIgnoreCase(eventType)) {
                String reason = payload != null && payload.get("reason") != null ? String.valueOf(payload.get("reason")) : "Complaint feedback loop";
                handleComplaint(tenantId, workspaceId, email, reason);
            } else {
                log.warn("Ignoring unsupported delivery event type '{}'", eventType);
            }

        } catch (Exception e) {
            log.error("Failed to process delivery event: {}", envelope != null ? envelope.getEventId() : "unknown", e);
        }
    }

    private void handleBounce(String tenantId, String workspaceId, String email, String reason) {
        // Update subscriber status
        Optional<Subscriber> subscriber = subscriberRepository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, email);
        subscriber.ifPresent(s -> {
            s.setStatus(Subscriber.SubscriberStatus.BOUNCED);
            s.setBouncedAt(java.time.Instant.now());
            subscriberRepository.save(s);
        });

        // Add to suppression list
        if (!suppressionRepository.existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                tenantId, workspaceId, email, Suppression.SuppressionType.HARD_BOUNCE)) {
            Suppression suppression = new Suppression();
            suppression.setTenantId(tenantId);
            suppression.setWorkspaceId(workspaceId);
            suppression.setEmail(email);
            suppression.setSuppressionType(Suppression.SuppressionType.HARD_BOUNCE);
            suppression.setReason(reason);
            suppression.setSource("DELIVERY_FEEDBACK");
            suppressionRepository.save(suppression);
        }
    }

    private void handleUnsubscribe(String tenantId, String workspaceId, String email, String reason) {
        Optional<Subscriber> subscriber = subscriberRepository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, email);
        subscriber.ifPresent(s -> {
            s.setStatus(Subscriber.SubscriberStatus.UNSUBSCRIBED);
            s.setUnsubscribedAt(java.time.Instant.now());
            subscriberRepository.save(s);
        });

        if (!suppressionRepository.existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                tenantId, workspaceId, email, Suppression.SuppressionType.UNSUBSCRIBE)) {
            Suppression suppression = new Suppression();
            suppression.setTenantId(tenantId);
            suppression.setWorkspaceId(workspaceId);
            suppression.setEmail(email);
            suppression.setSuppressionType(Suppression.SuppressionType.UNSUBSCRIBE);
            suppression.setReason(reason);
            suppression.setSource("USER_ACTION");
            suppressionRepository.save(suppression);
        }
    }

    private void handleComplaint(String tenantId, String workspaceId, String email, String reason) {
        Optional<Subscriber> subscriber = subscriberRepository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, email);
        subscriber.ifPresent(s -> {
            s.setStatus(Subscriber.SubscriberStatus.COMPLAINED);
            subscriberRepository.save(s);
        });

        if (!suppressionRepository.existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                tenantId, workspaceId, email, Suppression.SuppressionType.COMPLAINT)) {
            Suppression suppression = new Suppression();
            suppression.setTenantId(tenantId);
            suppression.setWorkspaceId(workspaceId);
            suppression.setEmail(email);
            suppression.setSuppressionType(Suppression.SuppressionType.COMPLAINT);
            suppression.setReason(reason);
            suppression.setSource("FEEDBACK_LOOP");
            suppressionRepository.save(suppression);
        }
    }
}
