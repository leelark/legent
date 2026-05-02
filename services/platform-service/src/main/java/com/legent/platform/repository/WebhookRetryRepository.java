package com.legent.platform.repository;

import java.time.Instant;
import java.util.List;

import com.legent.platform.domain.WebhookRetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * AUDIT-015: Repository for webhook retry records.
 */
@Repository
public interface WebhookRetryRepository extends JpaRepository<WebhookRetry, String> {

    /**
     * Find all pending retries scheduled for now or earlier.
     */
    @Query("SELECT r FROM WebhookRetry r WHERE r.status = 'PENDING' AND r.nextRetryAt <= :now ORDER BY r.createdAt ASC")
    List<WebhookRetry> findPendingRetries(@Param("now") Instant now);

    /**
     * Find all retrying records that should be processed.
     */
    @Query("SELECT r FROM WebhookRetry r WHERE r.status = 'RETRYING' AND r.nextRetryAt <= :now ORDER BY r.createdAt ASC")
    List<WebhookRetry> findRetryingRecords(@Param("now") Instant now);

    /**
     * Count pending retries for a tenant.
     */
    Long countByTenantIdAndStatus(String tenantId, String status);

    /**
     * Find all failed records for a tenant.
     */
    List<WebhookRetry> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);
}
