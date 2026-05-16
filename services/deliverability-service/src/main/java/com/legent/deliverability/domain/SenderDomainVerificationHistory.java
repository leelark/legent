package com.legent.deliverability.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sender_domain_verification_history")
@Getter
@Setter
public class SenderDomainVerificationHistory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "sender_domain_id", nullable = false, length = 36)
    private String senderDomainId;

    @Column(name = "domain_name", nullable = false)
    private String domainName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SenderDomain.VerificationStatus status;

    @Column(name = "spf_verified", nullable = false)
    private boolean spfVerified;

    @Column(name = "dkim_verified", nullable = false)
    private boolean dkimVerified;

    @Column(name = "dmarc_verified", nullable = false)
    private boolean dmarcVerified;

    @Column(name = "ownership_token_verified", nullable = false)
    private boolean ownershipTokenVerified;

    @Column(name = "verification_record_name")
    private String verificationRecordName;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;
}
