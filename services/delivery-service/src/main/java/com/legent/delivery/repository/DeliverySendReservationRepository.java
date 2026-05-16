package com.legent.delivery.repository;

import com.legent.delivery.domain.DeliverySendReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeliverySendReservationRepository extends JpaRepository<DeliverySendReservation, String> {

    Optional<DeliverySendReservation> findByTenantIdAndWorkspaceIdAndReservationId(
            String tenantId, String workspaceId, String reservationId);

    long countByTenantIdAndWorkspaceIdAndStatus(String tenantId, String workspaceId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM DeliverySendReservation r
            WHERE r.tenantId = :tenantId
              AND r.workspaceId = :workspaceId
              AND r.reservationId = :reservationId
              AND r.deletedAt IS NULL
            """)
    Optional<DeliverySendReservation> findActiveForUpdate(@Param("tenantId") String tenantId,
                                                          @Param("workspaceId") String workspaceId,
                                                          @Param("reservationId") String reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM DeliverySendReservation r
            WHERE r.tenantId = :tenantId
              AND r.workspaceId = :workspaceId
              AND r.rateLimitKey = :rateLimitKey
              AND r.status = :status
              AND r.leaseExpiresAt <= :now
              AND r.deletedAt IS NULL
            """)
    List<DeliverySendReservation> findExpiredRateReservationsForUpdate(@Param("tenantId") String tenantId,
                                                                       @Param("workspaceId") String workspaceId,
                                                                       @Param("rateLimitKey") String rateLimitKey,
                                                                       @Param("status") String status,
                                                                       @Param("now") Instant now);
}
