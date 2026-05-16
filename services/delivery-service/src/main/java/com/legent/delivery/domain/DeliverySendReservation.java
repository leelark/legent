package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "delivery_send_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_delivery_send_reservation_scope",
                columnNames = {"tenant_id", "workspace_id", "reservation_id"}))
@Getter
@Setter
public class DeliverySendReservation extends BaseEntity {

    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_RELEASED = "RELEASED";

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "reservation_id", nullable = false, length = 255)
    private String reservationId;

    @Column(name = "rate_limit_key", nullable = false, length = 500)
    private String rateLimitKey;

    @Column(name = "sender_domain", nullable = false, length = 255)
    private String senderDomain;

    @Column(name = "provider_id", nullable = false, length = 64)
    private String providerId;

    @Column(name = "recipient_domain", length = 255)
    private String recipientDomain;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "max_per_minute", nullable = false)
    private Integer maxPerMinute;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "warmup_hourly_limit", nullable = false)
    private Integer warmupHourlyLimit;

    @Column(name = "warmup_daily_limit", nullable = false)
    private Integer warmupDailyLimit;

    @Column(name = "rate_window_started_at", nullable = false)
    private Instant rateWindowStartedAt;

    @Column(name = "warmup_hour_window_started_at", nullable = false)
    private Instant warmupHourWindowStartedAt;

    @Column(name = "warmup_day_window_started_at", nullable = false)
    private Instant warmupDayWindowStartedAt;

    @Column(name = "lease_expires_at", nullable = false)
    private Instant leaseExpiresAt;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "release_reason", columnDefinition = "TEXT")
    private String releaseReason;
}
