package com.legent.platform.repository;

import java.time.Instant;
import java.util.List;

import com.legent.platform.domain.WebhookRetry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUDIT-015: Repository for webhook retry records.
 */
@Repository
public interface WebhookRetryRepository extends JpaRepository<WebhookRetry, String> {

    /**
     * Find all pending retries scheduled for now or earlier.
     */
    @Query("SELECT r FROM WebhookRetry r WHERE r.status = 'PENDING' AND r.nextRetryAt <= :now ORDER BY r.createdAt ASC")
    List<WebhookRetry> findPendingRetries(@Param("now") Instant now, Pageable pageable);

    /**
     * Atomically claim a pending retry before async processing.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WebhookRetry r
            SET r.status = 'RETRYING', r.updatedAt = :claimedAt
            WHERE r.id = :id
              AND r.status = 'PENDING'
              AND r.nextRetryAt <= :now
            """)
    int claimPendingRetry(@Param("id") String id, @Param("now") Instant now, @Param("claimedAt") Instant claimedAt);

    /**
     * Find all retrying records that should be processed.
     */
    @Query("SELECT r FROM WebhookRetry r WHERE r.status = 'RETRYING' AND r.nextRetryAt <= :now ORDER BY r.createdAt ASC")
    List<WebhookRetry> findRetryingRecords(@Param("now") Instant now);

    /**
     * Count pending retries for a tenant.
     */
    Long countByTenantIdAndWorkspaceIdAndStatus(String tenantId, String workspaceId, String status);

    /**
     * Find all failed records for a tenant.
     */
    List<WebhookRetry> findByTenantIdAndWorkspaceIdAndStatusOrderByCreatedAtDesc(
            String tenantId, String workspaceId, String status);
}
