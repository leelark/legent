package com.legent.audience.domain;

import java.util.Map;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


/**
 * Segment definition with JSON rules for the segmentation engine.
 */
@Entity
@Table(name = "segments")
@Getter
@Setter
@NoArgsConstructor
public class Segment extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "team_id", length = 36)
    private String teamId;

    @Column(name = "assigned_owner_id", length = 36)
    private String assignedOwnerId;

    @Column(name = "ownership_scope", nullable = false, length = 30)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SegmentStatus status = SegmentStatus.DRAFT;

    @Column(name = "segment_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SegmentType segmentType = SegmentType.FILTER;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rules;

    @Column(name = "member_count", nullable = false)
    private long memberCount = 0;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(name = "evaluation_duration_ms")
    private Long evaluationDurationMs;

    @Column(name = "schedule_enabled", nullable = false)
    private boolean scheduleEnabled = false;

    @Column(name = "folder", length = 255)
    private String folder;

    @Column(name = "category", length = 128)
    private String category;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saved_filter", columnDefinition = "jsonb")
    private Map<String, Object> savedFilter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private java.util.List<String> tags;

    public enum SegmentStatus {
        DRAFT, ACTIVE, COMPUTING, ERROR, ARCHIVED
    }

    public enum SegmentType {
        FILTER, QUERY, MANUAL
    }
}
