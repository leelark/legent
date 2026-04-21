package com.legent.identity.event;

import com.legent.common.event.UserSignedUpEvent;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdentityEventPublisher {

    private final EventPublisher eventPublisher;

    public void publishSignup(String tenantId, String userId, String email, String companyName, String slug) {
        UserSignedUpEvent payload = UserSignedUpEvent.builder()
                .userId(userId)
                .email(email)
                .companyName(companyName)
                .slug(slug)
                .build();

        eventPublisher.publish("identity.user.signup", 
                EventEnvelope.wrap("UserSignedUp", tenantId, "identity-service", payload));
    }
}
