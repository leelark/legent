package com.legent.delivery.domain;

import java.time.Instant;
import java.math.BigDecimal;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Provider health check record for monitoring SMTP provider health.
 */
@Entity
@Table(name = "provider_health_checks")
@Getter
@Setter
@NoArgsConstructor
public class ProviderHealthCheck extends TenantAwareEntity {

    @Column(name = "provider_id", nullable = false, length = 36)
    private String providerId;

    @Column(name = "check_timestamp", nullable = false)
    private Instant checkTimestamp = Instant.now();

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private HealthStatus status = HealthStatus.UNKNOWN;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "consecutive_failures")
    private Integer consecutiveFailures = 0;

    @Column(name = "success_rate_24h", columnDefinition = "DECIMAL(5,2)")
    private BigDecimal successRate24h;

    @Column(name = "total_sent_24h")
    private Long totalSent24h = 0L;

    @Column(name = "total_failed_24h")
    private Long totalFailed24h = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum HealthStatus {
        HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
    }

    public boolean isHealthy() {
        return status == HealthStatus.HEALTHY;
    }
}
