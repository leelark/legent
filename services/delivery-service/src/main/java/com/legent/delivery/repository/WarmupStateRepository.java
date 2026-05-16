package com.legent.delivery.repository;

import com.legent.delivery.domain.WarmupState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WarmupStateRepository extends JpaRepository<WarmupState, String> {
    Optional<WarmupState> findByTenantIdAndWorkspaceIdAndSenderDomainAndProviderId(
            String tenantId, String workspaceId, String senderDomain, String providerId);

    List<WarmupState> findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(String tenantId, String workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w FROM WarmupState w
            WHERE w.tenantId = :tenantId
              AND w.workspaceId = :workspaceId
              AND w.senderDomain = :senderDomain
              AND w.providerId = :providerId
              AND w.deletedAt IS NULL
            """)
    Optional<WarmupState> findActiveForUpdate(@Param("tenantId") String tenantId,
                                              @Param("workspaceId") String workspaceId,
                                              @Param("senderDomain") String senderDomain,
                                              @Param("providerId") String providerId);

    @Modifying
    @Query(value = """
            INSERT INTO delivery_warmup_state (
                id, tenant_id, workspace_id, sender_domain, provider_id, stage,
                hourly_limit, daily_limit, sent_this_hour, sent_today,
                hour_window_started_at, day_window_started_at, bounce_rate, complaint_rate,
                next_increase_at, created_at, updated_at, version
            ) VALUES (
                :id, :tenantId, :workspaceId, :senderDomain, :providerId, :stage,
                :hourlyLimit, :dailyLimit, 0, 0,
                :hourWindowStartedAt, :dayWindowStartedAt, 0, 0,
                :nextIncreaseAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            )
            ON CONFLICT (tenant_id, workspace_id, sender_domain, provider_id) WHERE deleted_at IS NULL DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") String id,
                       @Param("tenantId") String tenantId,
                       @Param("workspaceId") String workspaceId,
                       @Param("senderDomain") String senderDomain,
                       @Param("providerId") String providerId,
                       @Param("stage") String stage,
                       @Param("hourlyLimit") int hourlyLimit,
                       @Param("dailyLimit") int dailyLimit,
                       @Param("hourWindowStartedAt") Instant hourWindowStartedAt,
                       @Param("dayWindowStartedAt") Instant dayWindowStartedAt,
                       @Param("nextIncreaseAt") Instant nextIncreaseAt);
}
