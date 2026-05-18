package com.legent.campaign.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "send_batch_recipients")
@Getter
@Setter
public class SendBatchRecipient extends TenantAwareEntity {

    public enum RecipientStatus {
        PENDING, HANDOFFED, SUPPRESSED, FAILED
    }

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "subscriber_id", length = 64)
    private String subscriberId;

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecipientStatus status = RecipientStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "processed_at")
    private Instant processedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";
}
