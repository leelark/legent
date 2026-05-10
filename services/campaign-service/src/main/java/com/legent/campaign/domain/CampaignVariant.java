package com.legent.campaign.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "campaign_variants")
@Getter
@Setter
public class CampaignVariant extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(name = "experiment_id", nullable = false, length = 26)
    private String experimentId;

    @Column(name = "variant_key", nullable = false, length = 64)
    private String variantKey;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer weight = 50;

    @Column(name = "control_variant", nullable = false)
    private boolean controlVariant = false;

    @Column(name = "holdout_variant", nullable = false)
    private boolean holdoutVariant = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean winner = false;

    @Column(name = "content_id", length = 64)
    private String contentId;

    @Column(name = "subject_override", length = 500)
    private String subjectOverride;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String metadata = "{}";
}
