package com.legent.audience.event;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.domain.Suppression;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.audience.repository.SuppressionRepository;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = {AppConstants.TOPIC_EMAIL_BOUNCED, AppConstants.TOPIC_EMAIL_UNSUBSCRIBED}, groupId = "audience-delivery-group")
    public void handleDeliveryEvent(String message) {
        try {
            EventEnvelope<Map<String, String>> envelope = objectMapper.readValue(message, 
                    new TypeReference<EventEnvelope<Map<String, String>>>() {});
            
            String tenantId = envelope.getTenantId();
            String eventType = envelope.getEventType();
            Map<String, String> payload = envelope.getPayload();
            String email = payload.get("email");

            log.info("Processing delivery event: type={}, email={}, tenantId={}", eventType, email, tenantId);

            if (email == null) return;

            if ("EmailBounced".equalsIgnoreCase(eventType)) {
                handleBounce(tenantId, email, payload.get("reason"));
            } else if ("EmailUnsubscribed".equalsIgnoreCase(eventType)) {
                handleUnsubscribe(tenantId, email, payload.get("reason"));
            }

        } catch (Exception e) {
            log.error("Failed to process delivery event: {}", message, e);
        }
    }

    private void handleBounce(String tenantId, String email, String reason) {
        // Update subscriber status
        Optional<Subscriber> subscriber = subscriberRepository.findByTenantIdAndEmailAndDeletedAtIsNull(tenantId, email);
        subscriber.ifPresent(s -> {
            s.setStatus(Subscriber.SubscriberStatus.BOUNCED);
            subscriberRepository.save(s);
        });

        // Add to suppression list
        if (!suppressionRepository.existsByTenantIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(tenantId, email, Suppression.SuppressionType.HARD_BOUNCE)) {
            Suppression suppression = new Suppression();
            suppression.setTenantId(tenantId);
            suppression.setEmail(email);
            suppression.setSuppressionType(Suppression.SuppressionType.HARD_BOUNCE);
            suppression.setReason(reason);
            suppression.setSource("DELIVERY_FEEDBACK");
            suppressionRepository.save(suppression);
        }
    }

    private void handleUnsubscribe(String tenantId, String email, String reason) {
        Optional<Subscriber> subscriber = subscriberRepository.findByTenantIdAndEmailAndDeletedAtIsNull(tenantId, email);
        subscriber.ifPresent(s -> {
            s.setStatus(Subscriber.SubscriberStatus.UNSUBSCRIBED);
            subscriberRepository.save(s);
        });

        if (!suppressionRepository.existsByTenantIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(tenantId, email, Suppression.SuppressionType.UNSUBSCRIBE)) {
            Suppression suppression = new Suppression();
            suppression.setTenantId(tenantId);
            suppression.setEmail(email);
            suppression.setSuppressionType(Suppression.SuppressionType.UNSUBSCRIBE);
            suppression.setReason(reason);
            suppression.setSource("USER_ACTION");
            suppressionRepository.save(suppression);
        }
    }
}
