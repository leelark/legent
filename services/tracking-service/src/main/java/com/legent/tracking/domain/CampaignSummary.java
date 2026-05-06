package com.legent.tracking.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "campaign_summaries")
@Getter
@Setter
public class CampaignSummary extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "team_id")
    private String teamId;

    @Column(name = "ownership_scope", nullable = false)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    @Column(name = "total_sends")
    private Long totalSends = 0L;

    @Column(name = "total_opens")
    private Long totalOpens = 0L;

    @Column(name = "total_clicks")
    private Long totalClicks = 0L;

    @Column(name = "total_conversions")
    private Long totalConversions = 0L;

    @Column(name = "total_bounces")
    private Long totalBounces = 0L;

    @Column(name = "unique_opens")
    private Long uniqueOpens = 0L;

    @Column(name = "unique_clicks")
    private Long uniqueClicks = 0L;
}
