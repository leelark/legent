package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "delivery_safety_evaluations")
@Getter
@Setter
public class InboxSafetyEvaluation extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", length = 64)
    private String campaignId;

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "subscriber_id", length = 64)
    private String subscriberId;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "sender_domain", length = 255)
    private String senderDomain;

    @Column(name = "recipient_domain", length = 255)
    private String recipientDomain;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "max_rate_per_minute")
    private Integer maxRatePerMinute;

    @Column(name = "allowed_audience_count")
    private Integer allowedAudienceCount;

    @Column(name = "reason_codes", columnDefinition = "TEXT")
    private String reasonCodes;

    @Column(name = "remediation_hints", columnDefinition = "TEXT")
    private String remediationHints;

    @Column(name = "rate_limit_key", length = 500)
    private String rateLimitKey;

    @Column(name = "warmup_stage", length = 64)
    private String warmupStage;
}
