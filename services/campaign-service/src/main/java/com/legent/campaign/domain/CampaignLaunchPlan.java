package com.legent.campaign.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "campaign_launch_plans")
@Getter
@Setter
public class CampaignLaunchPlan extends TenantAwareEntity {

    public enum LaunchPlanStatus {
        PREVIEWED,
        READY,
        BLOCKED,
        EXECUTED,
        FAILED
    }

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LaunchPlanStatus status = LaunchPlanStatus.PREVIEWED;

    @Column(name = "readiness_score", nullable = false)
    private Integer readinessScore = 0;

    @Column(name = "blocker_count", nullable = false)
    private Integer blockerCount = 0;

    @Column(name = "warning_count", nullable = false)
    private Integer warningCount = 0;

    @Column(name = "primary_action", nullable = false, length = 64)
    private String primaryAction = "RUN_READINESS";

    @Column(name = "audit_id", length = 64)
    private String auditId;

    @Column(name = "executed_at")
    private Instant executedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_json", columnDefinition = "jsonb")
    private String requestJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", columnDefinition = "jsonb")
    private String summaryJson = "{}";
}
