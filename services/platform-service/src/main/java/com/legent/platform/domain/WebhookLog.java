package com.legent.platform.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "webhook_logs")
@Getter
@Setter
public class WebhookLog {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "webhook_id", nullable = false)
    private String webhookId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "is_success", nullable = false)
    private Boolean isSuccess;

    @Column(name = "executed_at", insertable = false, updatable = false)
    private Instant executedAt;
}
