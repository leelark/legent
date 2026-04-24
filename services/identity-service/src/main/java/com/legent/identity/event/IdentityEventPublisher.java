package com.legent.identity.event;

import com.legent.common.constant.AppConstants;
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

        eventPublisher.publish(
                AppConstants.TOPIC_IDENTITY_USER_SIGNUP,
                EventEnvelope.wrap(AppConstants.TOPIC_IDENTITY_USER_SIGNUP, tenantId, "identity-service", payload)
        );
    }
}
