package com.legent.campaign.domain;

import java.util.ArrayList;

import java.util.List;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "campaigns")
@Getter
@Setter
public class Campaign extends TenantAwareEntity {

    public enum CampaignStatus {
        DRAFT, SCHEDULED, SENDING, PAUSED, COMPLETED, CANCELLED
    }

    public enum CampaignType {
        STANDARD, AUTOMATION, TRIGGERED
    }

    @Column(nullable = false)
    private String name;

    private String subject;
    private String preheader;
    
    @Column(name = "content_id")
    private String contentId;

    @Column(name = "scheduled_at")
    private java.time.Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignType type = CampaignType.STANDARD;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CampaignAudience> audiences = new ArrayList<>();

    // Approval workflow fields
    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired = false;

    @Column(name = "approved_by", length = 36)
    private String approvedBy;

    @Column(name = "approved_at")
    private java.time.Instant approvedAt;

    @Column(name = "current_approver", length = 36)
    private String currentApprover;

    public void addAudience(String type, String sourceId) {
        CampaignAudience ca = new CampaignAudience();
        ca.setCampaign(this);
        ca.setAudienceType(CampaignAudience.AudienceType.valueOf(type.toUpperCase()));
        ca.setAudienceId(sourceId);
        ca.setAction(CampaignAudience.AudienceAction.INCLUDE);
        ca.setTenantId(this.getTenantId());
        this.audiences.add(ca);
    }
}
