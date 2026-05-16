package com.legent.delivery.repository;

import com.legent.delivery.domain.SendRateState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SendRateStateRepository extends JpaRepository<SendRateState, String> {
    Optional<SendRateState> findByTenantIdAndWorkspaceIdAndRateLimitKey(String tenantId, String workspaceId, String rateLimitKey);
    List<SendRateState> findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(String tenantId, String workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM SendRateState s
            WHERE s.tenantId = :tenantId
              AND s.workspaceId = :workspaceId
              AND s.rateLimitKey = :rateLimitKey
              AND s.deletedAt IS NULL
            """)
    Optional<SendRateState> findActiveForUpdate(@Param("tenantId") String tenantId,
                                                @Param("workspaceId") String workspaceId,
                                                @Param("rateLimitKey") String rateLimitKey);

    @Modifying
    @Query(value = """
            INSERT INTO delivery_send_rate_state (
                id, tenant_id, workspace_id, rate_limit_key, sender_domain, provider_id, isp_domain,
                max_per_minute, used_this_minute, window_started_at, throttle_state, risk_score,
                last_adjusted_at, created_at, updated_at, version
            ) VALUES (
                :id, :tenantId, :workspaceId, :rateLimitKey, :senderDomain, :providerId, :recipientDomain,
                :maxPerMinute, 0, :windowStartedAt, :throttleState, :riskScore,
                :lastAdjustedAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            ON CONFLICT (tenant_id, workspace_id, rate_limit_key) WHERE deleted_at IS NULL DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") String id,
                       @Param("tenantId") String tenantId,
                       @Param("workspaceId") String workspaceId,
                       @Param("rateLimitKey") String rateLimitKey,
                       @Param("senderDomain") String senderDomain,
                       @Param("providerId") String providerId,
                       @Param("recipientDomain") String recipientDomain,
                       @Param("maxPerMinute") int maxPerMinute,
                       @Param("windowStartedAt") Instant windowStartedAt,
                       @Param("throttleState") String throttleState,
                       @Param("riskScore") int riskScore,
                       @Param("lastAdjustedAt") Instant lastAdjustedAt);
}
