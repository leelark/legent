package com.legent.identity.event;

import com.legent.common.constant.AppConstants;
import com.legent.common.event.UserSignedUpEvent;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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

    public void publishPasswordResetEmail(String tenantId,
                                          String workspaceId,
                                          String userId,
                                          String email,
                                          String resetUrl,
                                          String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("subject", "Reset your Legent password");
        payload.put("campaignId", "system-password-reset");
        payload.put("subscriberId", userId);
        payload.put("workspaceId", workspaceId);
        payload.put("messageId", "pwdreset-" + idempotencyKey);
        payload.put("htmlBody",
                "<html><body style=\"font-family:Arial,sans-serif\">" +
                        "<h2>Reset your password</h2>" +
                        "<p>Use secure link below to set new password.</p>" +
                        "<p><a href=\"" + resetUrl + "\">Reset Password</a></p>" +
                        "<p>If you did not request this, ignore this email.</p>" +
                        "</body></html>");
        payload.put("fromName", "Legent Security");
        payload.put("replyToEmail", "support@legent.local");

        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                tenantId,
                "identity-service",
                payload
        );
        envelope.setWorkspaceId(workspaceId);
        envelope.setActorId(userId);
        envelope.setOwnershipScope("WORKSPACE");
        envelope.setIdempotencyKey(idempotencyKey);
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope);
    }
}
