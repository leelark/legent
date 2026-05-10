package com.legent.campaign.domain;

import java.math.BigDecimal;
import java.time.Instant;

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

@Entity
@Table(name = "campaign_experiments")
@Getter
@Setter
public class CampaignExperiment extends TenantAwareEntity {

    public enum ExperimentType {
        AB, MULTIVARIATE
    }

    public enum ExperimentStatus {
        DRAFT, ACTIVE, PAUSED, COMPLETED, PROMOTED
    }

    public enum WinnerMetric {
        OPENS, CLICKS, CONVERSIONS, REVENUE, CUSTOM
    }

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "experiment_type", nullable = false, length = 32)
    private ExperimentType experimentType = ExperimentType.AB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExperimentStatus status = ExperimentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "winner_metric", nullable = false, length = 32)
    private WinnerMetric winnerMetric = WinnerMetric.CLICKS;

    @Column(name = "custom_metric_name", length = 128)
    private String customMetricName;

    @Column(name = "auto_promotion", nullable = false)
    private boolean autoPromotion = false;

    @Column(name = "min_recipients_per_variant", nullable = false)
    private Integer minRecipientsPerVariant = 100;

    @Column(name = "evaluation_window_hours", nullable = false)
    private Integer evaluationWindowHours = 24;

    @Column(name = "holdout_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal holdoutPercentage = BigDecimal.ZERO;

    @Column(name = "winner_variant_id", length = 26)
    private String winnerVariantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String factors = "[]";

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
