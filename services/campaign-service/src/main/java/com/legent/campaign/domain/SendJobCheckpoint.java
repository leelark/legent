package com.legent.campaign.domain;

import java.time.Instant;
import java.util.Map;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Checkpoint for send job progress tracking.
 * Enables resume and recovery functionality.
 */
@Entity
@Table(name = "send_job_checkpoints")
@Getter
@Setter
@NoArgsConstructor
public class SendJobCheckpoint extends TenantAwareEntity {

    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @Column(name = "checkpoint_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CheckpointType checkpointType;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "last_processed_id", length = 36)
    private String lastProcessedId;

    @Column(name = "processed_count", nullable = false)
    private Long processedCount = 0L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum CheckpointType {
        BATCH, TIME_BASED, MANUAL, RECOVERY
    }
}
