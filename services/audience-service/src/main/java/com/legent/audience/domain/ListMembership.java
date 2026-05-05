package com.legent.audience.domain;

import java.time.Instant;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * List membership join entity.
 */
@Entity
@Table(name = "list_memberships")
@Getter
@Setter
@NoArgsConstructor
public class ListMembership extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "ownership_scope", nullable = false, length = 30)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "list_id", nullable = false, length = 36)
    private String listId;

    @Column(name = "subscriber_id", nullable = false, length = 36)
    private String subscriberId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "removed_at")
    private Instant removedAt;

    public enum MembershipStatus {
        ACTIVE, REMOVED, UNSUBSCRIBED
    }
}
