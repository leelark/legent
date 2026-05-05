package com.legent.audience.domain;

import java.util.List;

import java.util.Map;

import java.time.Instant;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


/**
 * Import job tracking entity.
 * Tracks progress, errors, and status of async CSV imports.
 */
@Entity
@Table(name = "import_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "team_id", length = 36)
    private String teamId;

    @Column(name = "assigned_owner_id", length = 36)
    private String assignedOwnerId;

    @Column(name = "ownership_scope", nullable = false, length = 30)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType = "SUBSCRIBER";

    @Column(name = "import_type", nullable = false, length = 30)
    private String importType = "CSV";

    @Column(name = "target_id", length = 36)
    private String targetId;

    @Column(name = "resume_token", length = 128)
    private String resumeToken;

    @Column(name = "started_by", length = 36)
    private String startedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_mapping", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> fieldMapping;

    @Column(name = "total_rows", nullable = false)
    private long totalRows = 0;

    @Column(name = "processed_rows", nullable = false)
    private long processedRows = 0;

    @Column(name = "success_rows", nullable = false)
    private long successRows = 0;

    @Column(name = "error_rows", nullable = false)
    private long errorRows = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", columnDefinition = "jsonb")
    private List<Map<String, Object>> errors;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum ImportStatus {
        PENDING, VALIDATING, PROCESSING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED, CANCELLED
    }
}
