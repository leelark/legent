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

@Entity
@Table(name = "campaign_launch_steps")
@Getter
@Setter
public class CampaignLaunchStep extends TenantAwareEntity {

    public enum LaunchStepStatus {
        PASS,
        WARN,
        BLOCKED,
        EXECUTED,
        SKIPPED,
        FAILED
    }

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "launch_plan_id", nullable = false, length = 26)
    private String launchPlanId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(name = "step_key", nullable = false, length = 64)
    private String stepKey;

    @Column(name = "step_label", nullable = false, length = 160)
    private String stepLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LaunchStepStatus status = LaunchStepStatus.PASS;

    @Column(name = "score", nullable = false)
    private Integer score = 0;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "jsonb")
    private String detailsJson = "{}";
}
