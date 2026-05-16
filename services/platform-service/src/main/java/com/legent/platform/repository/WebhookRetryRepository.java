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
            SET r.status = 'RETRYING', r.updatedAt = :claimedAt, r.claimStartedAt = :claimedAt
            WHERE r.id = :id
              AND r.status = 'PENDING'
              AND r.nextRetryAt <= :now
            """)
    int claimPendingRetry(@Param("id") String id, @Param("now") Instant now, @Param("claimedAt") Instant claimedAt);

    /**
     * Find retrying records whose claim lease has expired.
     */
    @Query("""
            SELECT r FROM WebhookRetry r
            WHERE r.status = 'RETRYING'
              AND (
                r.claimStartedAt <= :staleBefore
                OR (r.claimStartedAt IS NULL AND r.updatedAt <= :staleBefore)
              )
            ORDER BY r.updatedAt ASC, r.createdAt ASC
            """)
    List<WebhookRetry> findStaleRetryingRecords(@Param("staleBefore") Instant staleBefore, Pageable pageable);

    /**
     * Release a stale claim so a later scheduler tick can retry the webhook.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WebhookRetry r
            SET r.status = 'PENDING',
                r.nextRetryAt = :now,
                r.lastError = :reason,
                r.updatedAt = :now,
                r.claimStartedAt = NULL
            WHERE r.id = :id
              AND r.status = 'RETRYING'
              AND (
                r.claimStartedAt <= :staleBefore
                OR (r.claimStartedAt IS NULL AND r.updatedAt <= :staleBefore)
              )
            """)
    int releaseStaleRetryingRecord(@Param("id") String id,
                                   @Param("staleBefore") Instant staleBefore,
                                   @Param("now") Instant now,
                                   @Param("reason") String reason);

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
