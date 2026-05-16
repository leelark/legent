package com.legent.deliverability.domain;

import java.time.Instant;

import com.legent.common.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
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
    private String workspaceId;

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

    @JsonIgnore
    @Column(name = "verification_token_hash", length = 128)
    private String verificationTokenHash;

    @Column(name = "verification_record_name")
    private String verificationRecordName;

    @Column(name = "verification_record_value")
    private String verificationRecordValue;

    @Column(name = "verification_token_issued_at")
    private Instant verificationTokenIssuedAt;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;

    @Column(name = "ownership_token_verified")
    private Boolean ownershipTokenVerified = false;

    @Column(name = "ownership_token_verified_at")
    private Instant ownershipTokenVerifiedAt;

    @Column(name = "verification_failure_reason", length = 512)
    private String verificationFailureReason;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Transient
    private String verificationToken;
}

