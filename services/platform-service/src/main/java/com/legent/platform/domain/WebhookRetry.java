package com.legent.platform.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * AUDIT-015: Persistent store for failed webhooks pending retry.
 * Records webhook delivery failures for later retry attempts.
 */
@Entity
@Table(name = "webhook_retries")
@Getter
@Setter
public class WebhookRetry {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workspace_id")
    private String workspaceId;

    @Column(name = "webhook_id", nullable = false)
    private String webhookId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // PENDING, RETRYING, SUCCESS, FAILED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
