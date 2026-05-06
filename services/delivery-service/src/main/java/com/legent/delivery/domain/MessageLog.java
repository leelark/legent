package com.legent.delivery.domain;

import java.time.Instant;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "message_logs")
@Getter
@Setter
public class MessageLog extends BaseEntity {

    public enum DeliveryStatus {
        PENDING, PROCESSING, SENT, FAILED, BOUNCED, COMPLAINED
    }

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "campaign_id")
    private String campaignId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(name = "ownership_scope", nullable = false, length = 32)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "subscriber_id")
    private String subscriberId;

    @Column(nullable = false)
    private String email;

    @Column(name = "provider_id")
    private String providerId;

    @Column(nullable = false)
    private String status = DeliveryStatus.PENDING.name();

    @Column(name = "attempt_count")
    private Integer attemptCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "provider_response", columnDefinition = "TEXT")
    private String providerResponse;

    @Column(name = "failure_class", length = 64)
    private String failureClass;

    @Column(name = "from_email", length = 320)
    private String fromEmail;

    @Column(name = "from_name", length = 255)
    private String fromName;

    @Column(name = "reply_to_email", length = 320)
    private String replyToEmail;

    // Fix 31: Removed subject and htmlBody fields to prevent storage bloat
    // (20-100KB per email * 1M emails/day = 20-100GB/day)
    // Content is now fetched from content-service at retry time using contentReference

    @Column(name = "content_reference")
    private String contentReference;
}

