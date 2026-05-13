package com.legent.kafka.producer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


/**
 * Generic event publisher for sending domain events to Kafka.
 * All services use this to publish events in a consistent way.
 */
@Slf4j
@Component
@RequiredArgsConstructor

public class EventPublisher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> HIGH_VOLUME_TOPICS = Set.of(
            AppConstants.TOPIC_SEND_REQUESTED,
            AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED,
            AppConstants.TOPIC_AUDIENCE_RESOLVED,
            AppConstants.TOPIC_SEND_PROCESSING,
            AppConstants.TOPIC_BATCH_CREATED,
            AppConstants.TOPIC_BATCH_COMPLETED,
            AppConstants.TOPIC_SEND_COMPLETED,
            AppConstants.TOPIC_SEND_FAILED,
            AppConstants.TOPIC_SUBSCRIBER_CREATED,
            AppConstants.TOPIC_SUBSCRIBER_UPDATED,
            AppConstants.TOPIC_SUBSCRIBER_DELETED,
            AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
            AppConstants.TOPIC_EMAIL_SENT,
            AppConstants.TOPIC_EMAIL_FAILED,
            AppConstants.TOPIC_EMAIL_FAILED_DLQ,
            AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED,
            AppConstants.TOPIC_EMAIL_BOUNCED,
            AppConstants.TOPIC_EMAIL_COMPLAINT,
            AppConstants.TOPIC_EMAIL_UNSUBSCRIBED,
            AppConstants.TOPIC_EMAIL_OPEN,
            AppConstants.TOPIC_EMAIL_CLICK,
            AppConstants.TOPIC_EMAIL_DELIVERED,
            AppConstants.TOPIC_CONVERSION_EVENT,
            AppConstants.TOPIC_TRACKING_INGESTED,
            AppConstants.TOPIC_WORKFLOW_TRIGGER
    );
    private static final Set<String> HIGH_VOLUME_KEY_FIELDS = orderedSet(
            "partitionKey",
            "routingKey",
            "shardKey",
            "shardId",
            "messageId",
            "mid",
            "subscriberId",
            "subscriberKey",
            "recipientId",
            "batchId",
            "jobId",
            "providerId",
            "provider",
            "senderDomain",
            "sendingDomain",
            "domainId",
            "domain",
            "campaignId",
            "email",
            "recipientEmail"
    );

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes an event envelope to the specified topic.
     * Uses high-cardinality metadata for high-volume topics and tenant fallback for low-volume topics.
     */
    public <T> CompletableFuture<SendResult<String, Object>> publish(
            @NonNull String topic,
            @NonNull EventEnvelope<T> envelope) {

        String key = resolvePartitionKey(topic, envelope, null);

        log.info("Publishing event [{}] to topic [{}] with key [{}]",
                envelope.getEventType(), topic, key);

        return kafkaTemplate.send(topic, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event [{}] to [{}]: {}",
                                envelope.getEventId(), topic, ex.getMessage());
                    } else {
                        log.debug("Event [{}] published to [{}] partition [{}] offset [{}]",
                                envelope.getEventId(),
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Publishes with a custom partition key.
     */
    public <T> CompletableFuture<SendResult<String, Object>> publish(
            @NonNull String topic,
            @NonNull String partitionKey,
            @NonNull EventEnvelope<T> envelope) {

        String key = resolvePartitionKey(topic, envelope, partitionKey);

        log.info("Publishing event [{}] to topic [{}] with custom key [{}]",
                envelope.getEventType(), topic, key);

        return kafkaTemplate.send(topic, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event [{}] to [{}]: {}",
                                envelope.getEventId(), topic, ex.getMessage());
                    }
                });
    }

    private <T> String resolvePartitionKey(String topic, EventEnvelope<T> envelope, String explicitKey) {
        if (!isHighVolumeTopic(topic)) {
            return normalize(explicitKey)
                    .or(() -> normalize(envelope.getTenantId()))
                    .or(() -> normalize(envelope.getEventId()))
                    .orElse("SYSTEM");
        }

        Optional<String> normalizedExplicitKey = normalize(explicitKey);
        if (normalizedExplicitKey.isPresent() && !isTenantKey(normalizedExplicitKey.get(), envelope)) {
            return normalizedExplicitKey.get();
        }

        Optional<String> derivedKey = highVolumeKeyFrom(envelope);
        if (derivedKey.isPresent()) {
            if (normalizedExplicitKey.isPresent()) {
                log.warn("Replacing tenant partition key for high-volume topic [{}] with payload routing key", topic);
            }
            return derivedKey.get();
        }

        Optional<String> eventKey = normalize(envelope.getEventId())
                .or(() -> normalize(envelope.getIdempotencyKey()))
                .or(() -> normalize(envelope.getCorrelationId()));
        if (eventKey.isPresent()) {
            log.warn("High-volume topic [{}] missing routing metadata; using event metadata key [{}]",
                    topic, eventKey.get());
            return eventKey.get();
        }

        throw new IllegalArgumentException(
                "High-volume topic [" + topic + "] requires a non-tenant partition key or routing metadata");
    }

    private boolean isHighVolumeTopic(String topic) {
        return HIGH_VOLUME_TOPICS.contains(topic);
    }

    private <T> boolean isTenantKey(String key, EventEnvelope<T> envelope) {
        return normalize(envelope.getTenantId())
                .map(tenantId -> tenantId.equals(key))
                .orElse(false);
    }

    private Optional<String> highVolumeKeyFrom(EventEnvelope<?> envelope) {
        return keyFromPayload(envelope.getPayload());
    }

    private Optional<String> keyFromPayload(Object payload) {
        if (payload == null) {
            return Optional.empty();
        }
        if (payload instanceof Map<?, ?> map) {
            return keyFromMap(map);
        }
        if (payload instanceof CharSequence text) {
            return keyFromText(text.toString());
        }
        return keyFromObject(payload);
    }

    private Optional<String> keyFromMap(Map<?, ?> map) {
        for (String field : HIGH_VOLUME_KEY_FIELDS) {
            Optional<String> value = valueForKey(map, field);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> keyFromText(String payload) {
        Optional<String> normalized = normalize(payload);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        String text = normalized.get();
        if (!(text.startsWith("{") && text.endsWith("}"))) {
            return isLikelyIdentifier(text) ? Optional.of(text) : Optional.empty();
        }

        try {
            return keyFromMap(OBJECT_MAPPER.readValue(text, MAP_TYPE));
        } catch (Exception ex) {
            log.warn("Unable to parse high-volume event payload JSON for partition key: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> keyFromObject(Object payload) {
        try {
            Map<String, Object> map = OBJECT_MAPPER.convertValue(payload, MAP_TYPE);
            return keyFromMap(map);
        } catch (IllegalArgumentException ex) {
            log.warn("Unable to inspect high-volume event payload for partition key: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> valueForKey(Map<?, ?> map, String wantedKey) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key != null && wantedKey.equalsIgnoreCase(String.valueOf(key))) {
                return normalize(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private Optional<String> normalize(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private boolean isLikelyIdentifier(String value) {
        return value.length() <= 256 && value.chars().noneMatch(Character::isWhitespace);
    }

    private static Set<String> orderedSet(String... values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            set.add(value);
        }
        return Collections.unmodifiableSet(set);
    }
}
