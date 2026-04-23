package com.legent.audience.domain;

import java.time.Instant;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Materialized segment membership. Populated by the segmentation engine.
 */
@Entity
@Table(name = "segment_memberships")
@Getter
@Setter
@NoArgsConstructor
public class SegmentMembership extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "segment_id", nullable = false, length = 36)
    private String segmentId;

    @Column(name = "subscriber_id", nullable = false, length = 36)
    private String subscriberId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();
}
