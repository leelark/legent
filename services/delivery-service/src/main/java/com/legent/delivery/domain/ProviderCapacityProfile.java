package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "provider_capacity_profiles")
@Getter
@Setter
public class ProviderCapacityProfile extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "provider_id", nullable = false, length = 64)
    private String providerId;

    @Column(name = "sender_domain", length = 255)
    private String senderDomain;

    @Column(name = "isp_domain", length = 255)
    private String ispDomain;

    @Column(name = "hourly_cap", nullable = false)
    private Integer hourlyCap = 1000;

    @Column(name = "daily_cap", nullable = false)
    private Integer dailyCap = 10000;

    @Column(name = "current_max_per_minute", nullable = false)
    private Integer currentMaxPerMinute = 60;

    @Column(name = "min_success_rate", nullable = false)
    private Double minSuccessRate = 0.95;

    @Column(name = "observed_success_rate", nullable = false)
    private Double observedSuccessRate = 1.0;

    @Column(name = "bounce_rate", nullable = false)
    private Double bounceRate = 0.0;

    @Column(name = "complaint_rate", nullable = false)
    private Double complaintRate = 0.0;

    @Column(name = "backpressure_score", nullable = false)
    private Integer backpressureScore = 0;

    @Column(name = "failover_provider_id", length = 64)
    private String failoverProviderId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;
}
