package com.legent.delivery.repository;

import com.legent.delivery.domain.MessageLog;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface MessageLogRepository extends JpaRepository<MessageLog, String> {
    Optional<MessageLog> findByTenantIdAndMessageId(String tenantId, String messageId);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM MessageLog m WHERE m.status = 'PENDING' AND m.nextRetryAt <= :now")
    java.util.List<MessageLog> findEligibleForRetry(@org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @Modifying
    @Query("UPDATE MessageLog m SET m.status = :processingStatus WHERE m.id = :id AND m.status = :pendingStatus")
    int claimForProcessing(@Param("id") String id,
                           @Param("pendingStatus") String pendingStatus,
                           @Param("processingStatus") String processingStatus);
}
