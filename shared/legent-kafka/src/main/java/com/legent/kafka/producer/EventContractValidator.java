package com.legent.kafka.producer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates outbound Kafka contracts before events are handed to Kafka.
 */
@Component
public class EventContractValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<Integer> SCHEMA_V1 = Set.of(1);
    private static final Set<Integer> SCHEMA_V1_V2 = Set.of(1, 2);
    private static final Map<String, EventContract> CONTRACTS = Map.of(
            AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
            new EventContract(
                    SCHEMA_V1,
                    WorkspaceRequirement.ENVELOPE_OR_PAYLOAD,
                    List.of(
                            List.of("email"),
                            List.of("contentReference")),
                    List.of()),
            AppConstants.TOPIC_SEND_REQUESTED,
            new EventContract(
                    SCHEMA_V1,
                    WorkspaceRequirement.ENVELOPE_OR_PAYLOAD,
                    List.of(
                            List.of("campaignId"),
                            List.of("confirmLaunch"),
                            List.of("idempotencyKey")),
                    List.of()),
            AppConstants.TOPIC_AUDIENCE_RESOLVED,
            new EventContract(
                    SCHEMA_V1_V2,
                    WorkspaceRequirement.ENVELOPE_ONLY,
                    List.of(
                            List.of("campaignId"),
                            List.of("jobId"),
                            List.of("chunkId"),
                            List.of("chunkIndex"),
                            List.of("totalChunks"),
                            List.of("chunkSize"),
                            List.of("totalResolvedSubscribers"),
                            List.of("isLastChunk")),
                    List.of()),
            AppConstants.TOPIC_TRACKING_INGESTED,
            new EventContract(
                    SCHEMA_V1,
                    WorkspaceRequirement.ENVELOPE_ONLY,
                    List.of(
                            List.of("eventType"),
                            List.of("messageId", "workflowRunId", "subscriberId", "campaignId")),
                    List.of()),
            AppConstants.TOPIC_CONTENT_PUBLISHED,
            new EventContract(
                    SCHEMA_V1,
                    WorkspaceRequirement.ENVELOPE_ONLY,
                    List.of(
                            List.of("templateId"),
                            List.of("versionNumber")),
                    List.of()),
            AppConstants.TOPIC_WORKFLOW_TRIGGER,
            new EventContract(
                    SCHEMA_V1,
                    WorkspaceRequirement.ENVELOPE_ONLY,
                    List.of(
                            List.of("workflowId"),
                            List.of("subscriberId")),
                    List.of()),
            AppConstants.TOPIC_EMAIL_BOUNCED,
            new EventContract(
                    SCHEMA_V1,
                    WorkspaceRequirement.ENVELOPE_OR_PAYLOAD,
                    List.of(List.of("email")),
                    List.of(
                            List.of("bounceType", "type"),
                            List.of("senderDomain", "domainId"))),
            AppConstants.TOPIC_EMAIL_COMPLAINT,
            new EventContract(
                    SCHEMA_V1,
                    WorkspaceRequirement.ENVELOPE_OR_PAYLOAD,
                    List.of(List.of("email")),
                    List.of(List.of("senderDomain", "domainId")))
    );

    public void validate(String topic, EventEnvelope<?> envelope) {
        requireNonBlank(topic, "topic");
        if (envelope == null) {
            throw new IllegalArgumentException("Kafka event envelope is required for topic [" + topic + "]");
        }
        requireNonBlank(envelope.getEventType(), "eventType");
        if (envelope.getRetryCount() < 0) {
            throw new IllegalArgumentException("retryCount cannot be negative for topic [" + topic + "]");
        }
        if (envelope.getSchemaVersion() < 0) {
            throw new IllegalArgumentException("schemaVersion cannot be negative for topic [" + topic + "]");
        }

        EventContract contract = CONTRACTS.get(topic);
        if (contract == null) {
            return;
        }

        if (!topic.equals(envelope.getEventType())) {
            throw new IllegalArgumentException("eventType [" + envelope.getEventType()
                    + "] must match topic [" + topic + "]");
        }
        requireNonBlank(envelope.getEventId(), "eventId");
        requireNonBlank(envelope.getTenantId(), "tenantId");
        requireNonBlank(envelope.getSource(), "source");
        requireNonBlank(envelope.getCorrelationId(), "correlationId");
        requireNonBlank(envelope.getIdempotencyKey(), "idempotencyKey");
        if (envelope.getTimestamp() == null) {
            throw new IllegalArgumentException("timestamp is required for topic [" + topic + "]");
        }
        if (!contract.allowedSchemaVersions().contains(envelope.getSchemaVersion())) {
            throw new IllegalArgumentException("schemaVersion [" + envelope.getSchemaVersion()
                    + "] is not allowed for topic [" + topic + "]");
        }

        Map<String, Object> payload = payloadMap(topic, envelope.getPayload());
        validateWorkspace(topic, envelope, payload, contract.workspaceRequirement());
        validatePayloadKeys(topic, payload, contract);
        validateTopicSpecificContract(topic, envelope, payload);
    }

    private Map<String, Object> payloadMap(String topic, Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required for topic [" + topic + "]");
        }
        if (payload instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (payload instanceof CharSequence text) {
            String raw = text.toString().trim();
            if (raw.isEmpty()) {
                throw new IllegalArgumentException("payload is required for topic [" + topic + "]");
            }
            try {
                return normalizeMap(OBJECT_MAPPER.readValue(raw, MAP_TYPE));
            } catch (Exception ex) {
                throw new IllegalArgumentException("payload must be a JSON object for topic [" + topic + "]", ex);
            }
        }
        try {
            return normalizeMap(OBJECT_MAPPER.convertValue(payload, MAP_TYPE));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("payload must be object-like for topic [" + topic + "]", ex);
        }
    }

    private Map<String, Object> normalizeMap(Map<?, ?> map) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private void validateWorkspace(
            String topic,
            EventEnvelope<?> envelope,
            Map<String, Object> payload,
            WorkspaceRequirement requirement) {

        Optional<String> envelopeWorkspaceId = normalize(envelope.getWorkspaceId());
        Optional<String> payloadWorkspaceId = valueForKey(payload, "workspaceId");
        if (envelopeWorkspaceId.isPresent()
                && payloadWorkspaceId.isPresent()
                && !envelopeWorkspaceId.get().equals(payloadWorkspaceId.get())) {
            throw new IllegalArgumentException("workspaceId mismatch between envelope and payload for topic [" + topic + "]");
        }
        if (requirement == WorkspaceRequirement.ENVELOPE_ONLY && envelopeWorkspaceId.isEmpty()) {
            throw new IllegalArgumentException("workspaceId envelope metadata is required for topic [" + topic + "]");
        }
        if (requirement == WorkspaceRequirement.ENVELOPE_OR_PAYLOAD
                && envelopeWorkspaceId.isEmpty()
                && payloadWorkspaceId.isEmpty()) {
            throw new IllegalArgumentException("workspaceId is required in envelope or payload for topic [" + topic + "]");
        }
    }

    private void validatePayloadKeys(String topic, Map<String, Object> payload, EventContract contract) {
        for (List<String> requiredGroup : contract.requiredGroups()) {
            if (!hasAnyValue(payload, requiredGroup)) {
                throw new IllegalArgumentException("payload for topic [" + topic + "] requires "
                        + describeGroup(requiredGroup));
            }
        }
        for (List<String> alternativeGroup : contract.alternativeGroups()) {
            if (!hasAnyValue(payload, alternativeGroup)) {
                throw new IllegalArgumentException("payload for topic [" + topic + "] requires one of "
                        + alternativeGroup);
            }
        }
    }

    private void validateTopicSpecificContract(String topic, EventEnvelope<?> envelope, Map<String, Object> payload) {
        if (AppConstants.TOPIC_SEND_REQUESTED.equals(topic)) {
            validateCampaignSendRequested(topic, envelope, payload);
        } else if (AppConstants.TOPIC_AUDIENCE_RESOLVED.equals(topic)) {
            validateAudienceResolved(topic, envelope, payload);
        }
    }

    private void validateAudienceResolved(String topic, EventEnvelope<?> envelope, Map<String, Object> payload) {
        if (envelope.getSchemaVersion() == 1) {
            if (!hasAnyValue(payload, List.of("subscribers"))) {
                throw new IllegalArgumentException("payload for topic [" + topic + "] requires key [subscribers]");
            }
            return;
        }
        if (envelope.getSchemaVersion() == 2) {
            for (String field : List.of("chunkReferenceType", "subscriberStorage", "chunkUri")) {
                if (!hasAnyValue(payload, List.of(field))) {
                    throw new IllegalArgumentException("payload for topic [" + topic + "] requires key [" + field + "]");
                }
            }
            if (hasAnyValue(payload, List.of("subscribers"))) {
                throw new IllegalArgumentException("schemaVersion [2] audience resolved events must not embed subscribers");
            }
        }
    }

    private void validateCampaignSendRequested(String topic, EventEnvelope<?> envelope, Map<String, Object> payload) {
        Object confirmLaunch = rawValueForKey(payload, "confirmLaunch").orElse(null);
        if (!isExplicitTrue(confirmLaunch)) {
            throw new IllegalArgumentException("payload confirmLaunch must be true for topic [" + topic + "]");
        }

        String envelopeKey = normalize(envelope.getIdempotencyKey()).orElse(null);
        String payloadKey = rawValueForKey(payload, "idempotencyKey")
                .flatMap(this::normalize)
                .orElse(null);
        if (payloadKey == null) {
            throw new IllegalArgumentException("payload idempotencyKey is required for topic [" + topic + "]");
        }
        if (envelopeKey != null && !envelopeKey.equals(payloadKey)) {
            throw new IllegalArgumentException("idempotencyKey mismatch between envelope and payload for topic [" + topic + "]");
        }
    }

    private String describeGroup(List<String> group) {
        if (group.size() == 1) {
            return "key [" + group.get(0) + "]";
        }
        return "one of " + group;
    }

    private boolean hasAnyValue(Map<String, Object> payload, List<String> keys) {
        for (String key : keys) {
            if (valueForKey(payload, key).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> valueForKey(Map<String, Object> map, String wantedKey) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key != null && wantedKey.equalsIgnoreCase(key) && isPresentValue(entry.getValue())) {
                return normalize(entry.getValue()).or(() -> Optional.of("<present>"));
            }
        }
        return Optional.empty();
    }

    private Optional<Object> rawValueForKey(Map<String, Object> map, String wantedKey) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key != null && wantedKey.equalsIgnoreCase(key) && isPresentValue(entry.getValue())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private boolean isExplicitTrue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof CharSequence text) {
            return "true".equalsIgnoreCase(text.toString().trim());
        }
        return false;
    }

    private boolean isPresentValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence text) {
            return !text.toString().isBlank();
        }
        return true;
    }

    private Optional<String> normalize(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private record EventContract(
            Set<Integer> allowedSchemaVersions,
            WorkspaceRequirement workspaceRequirement,
            List<List<String>> requiredGroups,
            List<List<String>> alternativeGroups) {
    }

    private enum WorkspaceRequirement {
        ENVELOPE_ONLY,
        ENVELOPE_OR_PAYLOAD
    }
}
