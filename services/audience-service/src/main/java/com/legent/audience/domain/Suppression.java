package com.legent.audience.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Suppression record for compliance — prevents sending to suppressed emails.
 */
@Entity
@Table(name = "suppressions")
@Getter
@Setter
@NoArgsConstructor
public class Suppression extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "team_id", length = 36)
    private String teamId;

    @Column(name = "assigned_owner_id", length = 36)
    private String assignedOwnerId;

    @Column(name = "ownership_scope", nullable = false, length = 30)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "suppression_scope", nullable = false, length = 30)
    private String suppressionScope = "WORKSPACE";

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "suppression_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SuppressionType suppressionType;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(name = "suppressed_at", nullable = false)
    private Instant suppressedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "recovery_status", nullable = false, length = 30)
    private String recoveryStatus = "NONE";

    @Column(name = "recovered_at")
    private Instant recoveredAt;

    public enum SuppressionType {
        HARD_BOUNCE, SOFT_BOUNCE, COMPLAINT, UNSUBSCRIBE, MANUAL, INVALID_EMAIL, SPAM_TRAP
    }
}
