package com.legent.delivery.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Aggregated provider health status for quick lookup.
 */
@Entity
@Table(name = "provider_health_status")
@Getter
@Setter
@NoArgsConstructor
public class ProviderHealthStatus extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(name = "ownership_scope", nullable = false, length = 32)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "provider_id", nullable = false, length = 36, unique = true)
    private String providerId;

    @Column(name = "current_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private HealthStatus currentStatus = HealthStatus.UNKNOWN;

    @Column(name = "last_check_at")
    private Instant lastCheckAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "consecutive_failures")
    private Integer consecutiveFailures = 0;

    @Column(name = "circuit_breaker_open", nullable = false)
    private boolean circuitBreakerOpen = false;

    @Column(name = "health_score")
    private Integer healthScore = 100;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum HealthStatus {
        HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
    }

    public boolean isHealthy() {
        return currentStatus == HealthStatus.HEALTHY && !circuitBreakerOpen;
    }

    public void recordSuccess() {
        this.lastSuccessAt = Instant.now();
        this.consecutiveFailures = 0;
        this.currentStatus = HealthStatus.HEALTHY;
    }

    public void recordFailure() {
        this.lastFailureAt = Instant.now();
        this.consecutiveFailures++;
        if (this.consecutiveFailures >= 5) {
            this.currentStatus = HealthStatus.UNHEALTHY;
        } else if (this.consecutiveFailures >= 3) {
            this.currentStatus = HealthStatus.DEGRADED;
        }
    }
}

