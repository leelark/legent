package com.legent.campaign.domain;

import java.math.BigDecimal;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "campaign_variant_metrics")
@Getter
@Setter
public class CampaignVariantMetric extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(name = "experiment_id", nullable = false, length = 26)
    private String experimentId;

    @Column(name = "variant_id", length = 26)
    private String variantId;

    @Column(name = "target_count", nullable = false)
    private Long targetCount = 0L;

    @Column(name = "holdout_count", nullable = false)
    private Long holdoutCount = 0L;

    @Column(name = "sent_count", nullable = false)
    private Long sentCount = 0L;

    @Column(name = "failed_count", nullable = false)
    private Long failedCount = 0L;

    @Column(name = "open_count", nullable = false)
    private Long openCount = 0L;

    @Column(name = "click_count", nullable = false)
    private Long clickCount = 0L;

    @Column(name = "conversion_count", nullable = false)
    private Long conversionCount = 0L;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "custom_metric_count", nullable = false)
    private Long customMetricCount = 0L;
}
