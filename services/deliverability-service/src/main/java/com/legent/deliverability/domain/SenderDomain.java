package com.legent.deliverability.domain;

import java.time.Instant;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "sender_domains")
@Getter
@Setter
public class SenderDomain extends BaseEntity {

    public enum VerificationStatus {
        PENDING, VERIFIED, FAILED
    }

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId = "workspace-default";

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(name = "ownership_scope", nullable = false, length = 32)
    private String ownershipScope = "WORKSPACE";
    
    @Column(name = "status", nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "domain_name", nullable = false)
    private String domainName;

    @Column(name = "dkim_selector")
    private String dkimSelector = "legent";

    @Column(name = "is_active")
    private Boolean isActive = false;

    @Column(name = "spf_verified")
    private Boolean spfVerified = false;

    @Column(name = "dkim_verified")
    private Boolean dkimVerified = false;

    @Column(name = "dmarc_verified")
    private Boolean dmarcVerified = false;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;
}
