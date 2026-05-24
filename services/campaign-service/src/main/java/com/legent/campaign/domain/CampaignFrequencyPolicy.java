package com.legent.campaign.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "campaign_frequency_policies")
@Getter
@Setter
public class CampaignFrequencyPolicy extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "max_sends", nullable = false)
    private Integer maxSends = 0;

    @Column(name = "window_hours", nullable = false)
    private Integer windowHours = 24;

    @Column(name = "include_journeys", nullable = false)
    private boolean includeJourneys = true;

    @Column(name = "optimization_policy_key", length = 128)
    private String optimizationPolicyKey;

    @Column(name = "optimization_run_id", length = 64)
    private String optimizationRunId;

    @Column(name = "optimization_snapshot_hash", length = 128)
    private String optimizationSnapshotHash;

    @Column(name = "optimization_recommended_max_sends")
    private Integer optimizationRecommendedMaxSends;

    @Column(name = "optimization_approved", nullable = false)
    private boolean optimizationApproved = false;

    @Column(name = "optimization_approved_at")
    private Instant optimizationApprovedAt;
}
