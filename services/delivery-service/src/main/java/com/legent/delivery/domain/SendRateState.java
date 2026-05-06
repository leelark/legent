package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "delivery_send_rate_state")
@Getter
@Setter
public class SendRateState extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "rate_limit_key", nullable = false, length = 500)
    private String rateLimitKey;

    @Column(name = "sender_domain", length = 255)
    private String senderDomain;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Column(name = "isp_domain", length = 255)
    private String ispDomain;

    @Column(name = "max_per_minute", nullable = false)
    private Integer maxPerMinute;

    @Column(name = "used_this_minute", nullable = false)
    private Integer usedThisMinute;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "throttle_state", nullable = false, length = 32)
    private String throttleState;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "last_adjusted_at", nullable = false)
    private Instant lastAdjustedAt;
}
