package com.legent.campaign.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "send_jobs")
@Getter
@Setter
public class SendJob extends TenantAwareEntity {

    public enum JobStatus {
        PENDING, RESOLVING, BATCHING, SENDING, PAUSED, RETRYING, COMPLETED, FAILED, CANCELLED
    }

    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(name = "ownership_scope", nullable = false, length = 32)
    private String ownershipScope = "WORKSPACE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "total_target")
    private Long totalTarget = 0L;

    @Column(name = "total_sent")
    private Long totalSent = 0L;

    @Column(name = "total_failed")
    private Long totalFailed = 0L;

    @Column(name = "total_bounced")
    private Long totalBounced = 0L;

    @Column(name = "total_suppressed")
    private Long totalSuppressed = 0L;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "trigger_source", length = 128)
    private String triggerSource;

    @Column(name = "trigger_reference", length = 128)
    private String triggerReference;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    // Checkpointing fields
    @Column(name = "last_checkpoint_at")
    private Instant lastCheckpointAt;

    @Column(name = "checkpoint_interval")
    private Integer checkpointInterval = 1000;

    @Column(name = "can_resume", nullable = false)
    private boolean canResume = false;

    @Column(name = "resumed_from_job_id", length = 36)
    private String resumedFromJobId;
}
