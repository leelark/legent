package com.legent.campaign.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "campaign_audiences")
@Getter
@Setter
public class CampaignAudience extends TenantAwareEntity {

    public enum AudienceType {
        LIST, SEGMENT
    }

    public enum AudienceAction {
        INCLUDE, EXCLUDE
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false)
    private AudienceType audienceType;

    @Column(name = "audience_id", nullable = false)
    private String audienceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudienceAction action;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(name = "ownership_scope", nullable = false, length = 32)
    private String ownershipScope = "WORKSPACE";
}
