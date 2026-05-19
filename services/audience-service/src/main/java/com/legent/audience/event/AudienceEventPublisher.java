package com.legent.audience.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Event publisher for audience-related events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceEventPublisher {

    private final EventPublisher eventPublisher;

    @Value("${legent.consent.double-opt-in.confirmation-url-template:}")
    private String doubleOptInConfirmationUrlTemplate;

    @Value("${legent.consent.double-opt-in.from-email:}")
    private String doubleOptInFromEmail;

    @Value("${legent.consent.double-opt-in.from-name:Legent}")
    private String doubleOptInFromName;

    @Value("${legent.consent.double-opt-in.reply-to-email:}")
    private String doubleOptInReplyToEmail;

    @Value("${legent.consent.double-opt-in.subject:}")
    private String doubleOptInSubject;

    @Value("${legent.consent.double-opt-in.content-reference:}")
    private String doubleOptInContentReference;

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
    public CompletableFuture<SendResult<String, Object>> publishDoubleOptInRequested(
            String tenantId,
            String subscriberId,
            String email,
            String rawToken) {
        String confirmationUrl = confirmationUrl(tenantId, subscriberId, email, rawToken);
        String contentReference = requiredConfig(doubleOptInContentReference,
                "legent.consent.double-opt-in.content-reference");
        String subject = requiredConfig(doubleOptInSubject, "legent.consent.double-opt-in.subject");
        String fromEmail = requiredConfig(doubleOptInFromEmail, "legent.consent.double-opt-in.from-email");
        String workspaceId = TenantContext.requireWorkspaceId();
        String messageId = "doi-" + subscriberId + "-" + tokenFingerprint(rawToken);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subscriberId", subscriberId);
        payload.put("email", email);
        payload.put("workspaceId", workspaceId);
        payload.put("campaignId", "system-double-opt-in");
        payload.put("messageId", messageId);
        payload.put("contentReference", contentReference);
        payload.put("subject", subject);
        payload.put("htmlBody", doubleOptInHtmlBody(confirmationUrl));
        payload.put("textBody", "Confirm your subscription: " + confirmationUrl);
        payload.put("fromEmail", fromEmail);
        payload.put("fromName", normalize(doubleOptInFromName, "Legent"));
        String replyToEmail = normalize(doubleOptInReplyToEmail, null);
        if (replyToEmail != null) {
            payload.put("replyToEmail", replyToEmail);
        }
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                tenantId,
                "audience-service",
                payload
        );
        envelope.setWorkspaceId(workspaceId);
        envelope.setOwnershipScope("WORKSPACE");
        envelope.setIdempotencyKey(messageId);
        CompletableFuture<SendResult<String, Object>> future =
                eventPublisher.publish(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope);
        log.debug("Published double opt-in requested event for subscriber {}", subscriberId);
        return future;
    }

    private String confirmationUrl(String tenantId, String subscriberId, String email, String rawToken) {
        String template = requiredConfig(
                doubleOptInConfirmationUrlTemplate,
                "legent.consent.double-opt-in.confirmation-url-template");
        if (!template.contains("{token}")) {
            throw new IllegalStateException(
                    "legent.consent.double-opt-in.confirmation-url-template must include {token}");
        }
        return template
                .replace("{tenantId}", urlEncode(tenantId))
                .replace("{subscriberId}", urlEncode(subscriberId))
                .replace("{email}", urlEncode(email))
                .replace("{token}", urlEncode(rawToken));
    }

    private String doubleOptInHtmlBody(String confirmationUrl) {
        String escapedUrl = HtmlUtils.htmlEscape(confirmationUrl);
        return "<html><body style=\"font-family:Arial,sans-serif\">"
                + "<h2>Confirm your subscription</h2>"
                + "<p>Use the secure link below to confirm your email subscription.</p>"
                + "<p><a href=\"" + escapedUrl + "\">Confirm Subscription</a></p>"
                + "<p>If you did not request this, ignore this email.</p>"
                + "</body></html>";
    }

    private String requiredConfig(String value, String key) {
        String normalized = normalize(value, null);
        if (normalized == null) {
            throw new IllegalStateException(key + " is required before double opt-in email can be queued");
        }
        return normalized;
    }

    private String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String tokenFingerprint(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
