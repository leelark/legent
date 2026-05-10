package com.legent.campaign.domain;

import java.math.BigDecimal;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "campaign_budgets")
@Getter
@Setter
public class CampaignBudget extends TenantAwareEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_EXHAUSTED = "EXHAUSTED";

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "budget_limit", nullable = false, precision = 18, scale = 6)
    private BigDecimal budgetLimit = BigDecimal.ZERO;

    @Column(name = "cost_per_send", nullable = false, precision = 18, scale = 6)
    private BigDecimal costPerSend = BigDecimal.ZERO;

    @Column(name = "reserved_spend", nullable = false, precision = 18, scale = 6)
    private BigDecimal reservedSpend = BigDecimal.ZERO;

    @Column(name = "actual_spend", nullable = false, precision = 18, scale = 6)
    private BigDecimal actualSpend = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean enforced = false;

    @Column(nullable = false, length = 32)
    private String status = STATUS_OPEN;
}
