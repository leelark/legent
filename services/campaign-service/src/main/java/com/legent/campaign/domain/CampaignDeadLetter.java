package com.legent.campaign.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "campaign_dead_letters")
@Getter
@Setter
public class CampaignDeadLetter extends TenantAwareEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_REPLAYED = "REPLAYED";
    public static final String STATUS_IGNORED = "IGNORED";

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "subscriber_id", length = 64)
    private String subscriberId;

    @Column(length = 320)
    private String email;

    @Column(nullable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload = "{}";

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(nullable = false, length = 32)
    private String status = STATUS_OPEN;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;
}
