package com.legent.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "delivery_replay_queue")
@Getter
@Setter
public class DeliveryReplayQueue {

    public enum ReplayStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(name = "ownership_scope", nullable = false, length = 32)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "original_message_id", nullable = false)
    private String originalMessageId;

    @Column(name = "campaign_id")
    private String campaignId;

    @Column(name = "subscriber_id")
    private String subscriberId;

    @Column(nullable = false)
    private String email;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "replay_reason", nullable = false)
    private String replayReason;

    @Column(nullable = false)
    private String status = ReplayStatus.PENDING.name();

    @Column(nullable = false)
    private Integer priority = 5;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "failure_class")
    private String failureClass;

    @Column(name = "source_job_id")
    private String sourceJobId;

    @Column(name = "source_batch_id")
    private String sourceBatchId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}


