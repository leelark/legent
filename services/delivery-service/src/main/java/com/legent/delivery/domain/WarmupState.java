package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "delivery_warmup_state")
@Getter
@Setter
public class WarmupState extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "sender_domain", nullable = false, length = 255)
    private String senderDomain;

    @Column(name = "provider_id", nullable = false, length = 64)
    private String providerId;

    @Column(name = "stage", nullable = false, length = 64)
    private String stage;

    @Column(name = "hourly_limit", nullable = false)
    private Integer hourlyLimit;

    @Column(name = "daily_limit", nullable = false)
    private Integer dailyLimit;

    @Column(name = "sent_this_hour", nullable = false)
    private Integer sentThisHour;

    @Column(name = "sent_today", nullable = false)
    private Integer sentToday;

    @Column(name = "hour_window_started_at", nullable = false)
    private Instant hourWindowStartedAt;

    @Column(name = "day_window_started_at", nullable = false)
    private Instant dayWindowStartedAt;

    @Column(name = "bounce_rate")
    private Double bounceRate;

    @Column(name = "complaint_rate")
    private Double complaintRate;

    @Column(name = "rollback_reason", columnDefinition = "TEXT")
    private String rollbackReason;

    @Column(name = "next_increase_at", nullable = false)
    private Instant nextIncreaseAt;
}
