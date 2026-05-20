package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "send_governance_policies")
@Getter
@Setter
public class SendGovernancePolicy extends TenantAwareEntity {

    public enum Classification {
        COMMERCIAL,
        TRANSACTIONAL,
        OPERATIONAL
    }

    public enum UnsubscribePolicy {
        REQUIRED,
        OPTIONAL,
        NOT_APPLICABLE
    }

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "policy_key", nullable = false, length = 128)
    private String policyKey;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Classification classification = Classification.COMMERCIAL;

    @Column(name = "sender_profile_id", length = 64)
    private String senderProfileId;

    @Column(name = "delivery_profile_id", length = 64)
    private String deliveryProfileId;

    @Column(name = "sending_domain", length = 255)
    private String sendingDomain;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "unsubscribe_policy", nullable = false, length = 32)
    private UnsubscribePolicy unsubscribePolicy = UnsubscribePolicy.REQUIRED;

    @Column(name = "suppression_required", nullable = false)
    private Boolean suppressionRequired = Boolean.TRUE;

    @Column(name = "consent_required", nullable = false)
    private Boolean consentRequired = Boolean.FALSE;

    @Column(name = "tracking_allowed", nullable = false)
    private Boolean trackingAllowed = Boolean.TRUE;

    @Column(name = "send_log_retention_days", nullable = false)
    private Integer sendLogRetentionDays = 365;

    @Column(name = "publication_policy", nullable = false, length = 64)
    private String publicationPolicy = "APPROVED_CONTENT_REQUIRED";

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;
}
