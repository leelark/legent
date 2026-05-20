package com.legent.kafka.producer;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventContractValidatorTest {

    private final EventContractValidator validator = new EventContractValidator();

    @Test
    void validate_allowsCanonicalManagedEvent() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_AUDIENCE_RESOLVED,
                Map.of(
                        "campaignId", "campaign-1",
                        "jobId", "job-1",
                        "chunkId", "job-1:chunk:0",
                        "chunkIndex", 0,
                        "totalChunks", 1,
                        "chunkSize", 0,
                        "totalResolvedSubscribers", 0,
                        "isLastChunk", true,
                        "subscribers", List.of()
                )
        );

        assertDoesNotThrow(() -> validator.validate(AppConstants.TOPIC_AUDIENCE_RESOLVED, envelope));
    }

    @Test
    void validate_rejectsTopicEventTypeMismatchForManagedTopic() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                Map.of(
                        "email", "user@example.com",
                        "contentReference", "cr_123")
        );
        envelope.setEventType(AppConstants.TOPIC_EMAIL_BOUNCED);

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope));
    }

    @Test
    void validate_allowsEmailSendRequestedWithContentReference() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                Map.of(
                        "email", "user@example.com",
                        "contentReference", "cr_123",
                        "subject", "Hello",
                        "htmlBody", "<p>Hello</p>")
        );

        assertDoesNotThrow(() -> validator.validate(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope));
    }

    @Test
    void validate_allowsEmailSendRequestedWithReferenceOnlyPayload() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                Map.of(
                        "email", "user@example.com",
                        "contentReference", "cr_123")
        );

        assertDoesNotThrow(() -> validator.validate(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope));
    }

    @Test
    void validate_rejectsEmailSendRequestedWithTemplateOnlyPayload() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                Map.of(
                        "email", "user@example.com",
                        "templateId", "double-opt-in-confirmation",
                        "eventType", "DOUBLE_OPT_IN_REQUESTED")
        );

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope));
    }

    @Test
    void validate_rejectsEmailSendRequestedWithoutContentReference() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                Map.of(
                        "email", "user@example.com",
                        "subject", "Hello",
                        "htmlBody", "<p>Hello</p>")
        );

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope));
    }

    @Test
    void validate_rejectsEmailSendRequestedWithHtmlContentOnly() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                Map.of(
                        "email", "user@example.com",
                        "subject", "Hello",
                        "htmlContent", "<p>Hello</p>")
        );

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope));
    }

    @Test
    void validate_rejectsEmailSendRequestedWithBlankContentReference() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                Map.of(
                        "email", "user@example.com",
                        "contentReference", "   ",
                        "htmlBody", "<p>Hello</p>")
        );

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope));
    }

    @Test
    void validate_rejectsUnsupportedSchemaVersionForManagedTopic() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_WORKFLOW_TRIGGER,
                Map.of("workflowId", "workflow-1", "subscriberId", "subscriber-1")
        );
        envelope.setSchemaVersion(2);

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(AppConstants.TOPIC_WORKFLOW_TRIGGER, envelope));
    }

    @Test
    void validate_rejectsMissingWorkspaceForManagedTopic() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_TRACKING_INGESTED,
                Map.of("eventType", "OPEN", "messageId", "message-1")
        );
        envelope.setWorkspaceId(null);

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(AppConstants.TOPIC_TRACKING_INGESTED, envelope));
    }

    @Test
    void validate_acceptsJourneyConversionRoutingWithoutMessageId() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_TRACKING_INGESTED,
                Map.of(
                        "eventType", "CONVERSION",
                        "workflowRunId", "run-1",
                        "workflowId", "workflow-1",
                        "goalId", "goal-1")
        );

        assertDoesNotThrow(() -> validator.validate(AppConstants.TOPIC_TRACKING_INGESTED, envelope));
    }

    @Test
    void validate_rejectsMissingRequiredPayloadKey() {
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_BOUNCED,
                Map.of("email", "user@example.com", "type", "HARD_BOUNCE")
        );

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(AppConstants.TOPIC_EMAIL_BOUNCED, envelope));
    }

    @Test
    void validate_keepsUnknownTopicBackwardCompatible() {
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.<Map<String, Object>>builder()
                .eventId("event-1")
                .eventType("legacy.event")
                .tenantId("tenant-1")
                .payload(Map.of())
                .build();

        assertDoesNotThrow(() -> validator.validate("legacy.event", envelope));
    }

    private EventEnvelope<Map<String, Object>> envelope(String topic, Map<String, Object> payload) {
        return EventEnvelope.<Map<String, Object>>builder()
                .eventId("event-1")
                .eventType(topic)
                .timestamp(Instant.parse("2026-05-18T00:00:00Z"))
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .source("test")
                .schemaVersion(1)
                .correlationId("corr-1")
                .idempotencyKey("idem-1")
                .payload(payload)
                .build();
    }
}
