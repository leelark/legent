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
    
    @Column(name = "sender_profile_id")
    private String senderProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignType type = CampaignType.STANDARD;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CampaignAudience> audiences = new ArrayList<>();
}
