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
        PENDING, SENT, FAILED, BOUNCED, COMPLAINED
    }

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "campaign_id")
    private String campaignId;

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
}
