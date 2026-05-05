package com.legent.audience.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Consent record for GDPR/privacy compliance.
 * Tracks subscriber consent for email, SMS, data processing, etc.
 */
@Entity
@Table(name = "consent_records")
@Getter
@Setter
@NoArgsConstructor
public class ConsentRecord extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "team_id", length = 36)
    private String teamId;

    @Column(name = "assigned_owner_id", length = 36)
    private String assignedOwnerId;

    @Column(name = "ownership_scope", nullable = false, length = 30)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "subscriber_id", nullable = false, length = 36)
    private String subscriberId;

    @Column(name = "consent_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ConsentType consentType;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven = false;

    @Column(name = "consent_source", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ConsentSource consentSource = ConsentSource.WEB_FORM;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "consent_date", nullable = false)
    private Instant consentDate = Instant.now();

    @Column(name = "withdrawn_date")
    private Instant withdrawnDate;

    @Column(name = "legal_basis", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private LegalBasis legalBasis = LegalBasis.CONSENT;

    @Column(name = "privacy_version", length = 20)
    private String privacyVersion;

    @Column(name = "notes", length = 1000)
    private String notes;

    public enum ConsentType {
        EMAIL_MARKETING, SMS_MARKETING, PHONE_MARKETING,
        DATA_PROCESSING, PROFILING, THIRD_PARTY_SHARING,
        TRACKING, LOCATION_TRACKING
    }

    public enum ConsentSource {
        WEB_FORM, API, IMPORT, ADMIN, DOUBLE_OPT_IN,
        MOBILE_APP, PHONE_CALL, IN_PERSON, LEGACY_IMPORT
    }

    public enum LegalBasis {
        CONSENT, LEGITIMATE_INTEREST, LEGAL_OBLIGATION,
        CONTRACT, VITAL_INTEREST, PUBLIC_INTEREST
    }

    public boolean isActive() {
        return consentGiven && withdrawnDate == null;
    }
}
