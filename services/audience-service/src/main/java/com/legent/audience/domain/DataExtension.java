package com.legent.audience.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Data extension schema definition.
 * is_sendable = true means this DE can be used as a send audience.
 */
@Entity
@Table(name = "data_extensions")
@Getter
@Setter
@NoArgsConstructor
public class DataExtension extends TenantAwareEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "is_sendable", nullable = false)
    private boolean sendable = false;

    @Column(name = "sendable_field", length = 128)
    private String sendableField;

    @Column(name = "primary_key_field", length = 128)
    private String primaryKeyField;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "retention_action", length = 32)
    private String retentionAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relationship_json", columnDefinition = "jsonb")
    private String relationshipJson = "[]";

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType = "MANUAL";

    @Column(name = "source_system", length = 128)
    private String sourceSystem;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "data_classification", nullable = false, length = 32)
    private String dataClassification = "INTERNAL";

    @Column(name = "governance_notes", length = 1000)
    private String governanceNotes;

    @Column(name = "governance_reviewed_by", length = 36)
    private String governanceReviewedBy;

    @Column(name = "governance_reviewed_at")
    private Instant governanceReviewedAt;

    @Column(name = "record_count", nullable = false)
    private long recordCount = 0;
}
