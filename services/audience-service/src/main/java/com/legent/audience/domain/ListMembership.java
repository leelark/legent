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

    @Column(name = "tenant_id", nullable = false, length = 26)
    private String tenantId;

    @Column(name = "list_id", nullable = false, length = 26)
    private String listId;

    @Column(name = "subscriber_id", nullable = false, length = 26)
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
