package com.legent.automation.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "automation_artifacts")
@Getter
@Setter
public class AutomationArtifact extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "activity_id", length = 26)
    private String activityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 32)
    private SourceKind sourceKind = SourceKind.UPLOAD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArtifactStatus status = ArtifactStatus.READY;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "retention_policy", nullable = false, length = 64)
    private String retentionPolicy = "AUTOMATION_30_DAYS";

    @Column(name = "expires_at")
    private Instant expiresAt;

    public enum SourceKind {
        UPLOAD,
        INBOX,
        PROVIDER_EXPORT,
        GENERATED_EXTRACT,
        APPROVED_CONNECTOR
    }

    public enum ArtifactStatus {
        READY,
        GENERATED,
        EXPIRED,
        REVOKED,
        FAILED
    }
}
