package com.legent.campaign.domain;

import java.util.ArrayList;

import java.util.List;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name = "campaigns")
@Getter
@Setter
public class Campaign extends TenantAwareEntity {

    public enum CampaignStatus {
        DRAFT,
        REVIEW_PENDING,
        APPROVED,
        SCHEDULED,
        SENDING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED,
        ARCHIVED
    }

    public enum CampaignType {
        STANDARD, AUTOMATION, TRIGGERED
    }

    @Column(nullable = false)
    private String name;

    private String subject;
    private String preheader;

    @Column(name = "sender_profile_id", length = 64)
    private String senderProfileId;
    
    @Column(name = "content_id")
    private String contentId;

    @Column(name = "scheduled_at")
    private java.time.Instant scheduledAt;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(name = "ownership_scope", nullable = false, length = 32)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(name = "sender_email", length = 320)
    private String senderEmail;

    @Column(name = "reply_to_email", length = 320)
    private String replyToEmail;

    @Column(name = "brand_id", length = 64)
    private String brandId;

    @Column(name = "tracking_enabled", nullable = false)
    private Boolean trackingEnabled = Boolean.TRUE;

    @Column(name = "compliance_enabled", nullable = false)
    private Boolean complianceEnabled = Boolean.TRUE;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "sending_domain", length = 255)
    private String sendingDomain;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "quiet_hours_start")
    private java.time.LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private java.time.LocalTime quietHoursEnd;

    @Column(name = "send_window_start")
    private java.time.LocalTime sendWindowStart;

    @Column(name = "send_window_end")
    private java.time.LocalTime sendWindowEnd;

    @Column(name = "frequency_cap", nullable = false)
    private Integer frequencyCap = 0;

    @Column(name = "archived_at")
    private java.time.Instant archivedAt;

    @Column(name = "lifecycle_note")
    private String lifecycleNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "experiment_config", columnDefinition = "jsonb")
    private String experimentConfig = "{}";

    @Column(name = "trigger_source", length = 128)
    private String triggerSource;

    @Column(name = "trigger_reference", length = 128)
    private String triggerReference;

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

    public void addAudience(CampaignAudience.AudienceType type,
                            String sourceId,
                            CampaignAudience.AudienceAction action) {
        CampaignAudience ca = new CampaignAudience();
        ca.setCampaign(this);
        ca.setAudienceType(type);
        ca.setAudienceId(sourceId);
        ca.setAction(action);
        ca.setTenantId(this.getTenantId());
        ca.setWorkspaceId(this.workspaceId);
        ca.setTeamId(this.teamId);
        ca.setOwnershipScope(this.ownershipScope);
        this.audiences.add(ca);
    }

    public void addAudience(String type, String sourceId) {
        addAudience(
                CampaignAudience.AudienceType.valueOf(type.toUpperCase()),
                sourceId,
                CampaignAudience.AudienceAction.INCLUDE
        );
    }
}
