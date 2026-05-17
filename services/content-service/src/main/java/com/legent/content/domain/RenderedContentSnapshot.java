package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "rendered_content_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_rendered_content_snapshot_reference",
                        columnNames = {"tenant_id", "workspace_id", "reference_id"})
        })
@Getter
@Setter
public class RenderedContentSnapshot extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "reference_id", nullable = false, length = 80)
    private String referenceId;

    @Column(name = "campaign_id", nullable = false, length = 64)
    private String campaignId;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "message_id", nullable = false, length = 160)
    private String messageId;

    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "TEXT")
    private String htmlBody;

    @Column(name = "text_body", columnDefinition = "TEXT")
    private String textBody;

    @Column(name = "subject_sha256", nullable = false, length = 64)
    private String subjectSha256;

    @Column(name = "html_sha256", nullable = false, length = 64)
    private String htmlSha256;

    @Column(name = "text_sha256", length = 64)
    private String textSha256;

    @Column(name = "subject_bytes", nullable = false)
    private Integer subjectBytes;

    @Column(name = "html_bytes", nullable = false)
    private Integer htmlBytes;

    @Column(name = "text_bytes", nullable = false)
    private Integer textBytes;

    @Column(name = "inline_fallback_included", nullable = false)
    private Boolean inlineFallbackIncluded = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
