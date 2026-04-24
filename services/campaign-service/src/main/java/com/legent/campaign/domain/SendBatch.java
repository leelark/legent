package com.legent.campaign.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name = "send_batches")
@Getter
@Setter
public class SendBatch extends TenantAwareEntity {

    public enum BatchStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, PARTIAL
    }

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Column(name = "campaign_id")
    private String campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status = BatchStatus.PENDING;

    private String domain;

    @Column(name = "batch_size", nullable = false)
    private Integer batchSize = 0;

    @Column(name = "processed_count")
    private Integer processedCount = 0;

    @Column(name = "success_count")
    private Integer successCount = 0;

    @Column(name = "failure_count")
    private Integer failureCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "retry_count")
    private Integer retryCount = 0;
}
