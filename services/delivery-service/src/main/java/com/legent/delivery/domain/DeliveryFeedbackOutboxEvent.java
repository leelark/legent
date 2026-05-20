package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "delivery_feedback_outbox_events")
@Getter
@Setter
public class DeliveryFeedbackOutboxEvent extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHING = "PUBLISHING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_FAILED = "FAILED";

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "environment_id", length = 64)
    private String environmentId;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "ownership_scope", nullable = false, length = 32)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "source", nullable = false, length = 128)
    private String source;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId;

    @Column(name = "campaign_id", length = 64)
    private String campaignId;

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "subscriber_id", length = 64)
    private String subscriberId;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(name = "sender_domain", length = 255)
    private String senderDomain;

    @Column(name = "transition_key", nullable = false, length = 512)
    private String transitionKey;

    @Column(name = "partition_key", nullable = false, length = 512)
    private String partitionKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, length = 24)
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 8;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "published_at")
    private Instant publishedAt;
}
