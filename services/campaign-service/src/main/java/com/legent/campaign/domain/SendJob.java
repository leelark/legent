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
        PENDING, RESOLVING, BATCHING, SENDING, COMPLETED, FAILED, CANCELLED
    }

    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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
}
