package com.legent.tracking.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class TrackingEventIdempotencyService {

    private static final Duration DEFAULT_STALE_CLAIM_AGE = Duration.ofMinutes(15);

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${legent.tracking.idempotency.stale-claim-age:PT15M}")
    private Duration staleClaimAge = DEFAULT_STALE_CLAIM_AGE;

    public enum ClaimStatus {
        CLAIMED,
        RAW_WRITTEN,
        PROCESSED,
        IN_PROGRESS
    }

    @Transactional
    public boolean claimIfNew(String tenantId,
                              String workspaceId,
                              String eventType,
                              String eventId,
                              String idempotencyKey) {
        ClaimStatus status = claimForProcessing(tenantId, workspaceId, eventType, eventId, idempotencyKey);
        if (status == ClaimStatus.CLAIMED) {
            return true;
        }
        log.debug("Skipping duplicate tracking event tenant={}, workspace={}, type={}, eventId={}, idemKey={}, status={}",
                tenantId, workspaceId, eventType, eventId, idempotencyKey, status);
        return false;
    }

    @Transactional
    public ClaimStatus claimForProcessing(String tenantId,
                                          String workspaceId,
                                          String eventType,
                                          String eventId,
                                          String idempotencyKey) {
        ClaimKey claimKey = claimKey(eventId, idempotencyKey);
        for (int attempt = 0; attempt < 2; attempt++) {
            if (insertClaim(tenantId, workspaceId, eventType, claimKey)) {
                return ClaimStatus.CLAIMED;
            }
            Optional<ExistingClaim> existingClaim = findExistingClaim(tenantId, workspaceId, eventType, claimKey);
            if (existingClaim.isPresent()) {
                ExistingClaim claim = existingClaim.get();
                if (claim.status() == ClaimStatus.IN_PROGRESS
                        && reclaimStaleClaim(tenantId, workspaceId, eventType, claim)) {
                    return ClaimStatus.CLAIMED;
                }
                return claim.status();
            }
        }
        return ClaimStatus.IN_PROGRESS;
    }

    @Transactional
    public void markRawWritten(String tenantId,
                               String workspaceId,
                               String eventType,
                               String eventId,
                               String idempotencyKey) {
        int rows = updateClaim("""
                        UPDATE tracking_event_idempotency
                        SET raw_written_at = COALESCE(raw_written_at, ?), updated_at = ?, version = version + 1
                        WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                          AND %s = ?
                          AND processed_at IS NULL
                        """,
                tenantId, workspaceId, eventType, eventId, idempotencyKey, 2);
        if (rows != 1) {
            throw new IllegalStateException("Unable to mark tracking event idempotency claim raw-written");
        }
    }

    @Transactional
    public void markProcessed(String tenantId,
                              String workspaceId,
                              String eventType,
                              String eventId,
                              String idempotencyKey) {
        int rows = updateClaim("""
                        UPDATE tracking_event_idempotency
                        SET raw_written_at = COALESCE(raw_written_at, ?),
                            processed_at = ?,
                            updated_at = ?,
                            version = version + 1
                        WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                          AND %s = ?
                          AND processed_at IS NULL
                        """,
                tenantId, workspaceId, eventType, eventId, idempotencyKey, 3);
        if (rows != 1) {
            throw new IllegalStateException("Unable to mark tracking event idempotency claim processed");
        }
    }

    @Transactional
    public void releaseClaim(String tenantId,
                             String workspaceId,
                             String eventType,
                             String eventId,
                             String idempotencyKey) {
        updateClaim("""
                        DELETE FROM tracking_event_idempotency
                        WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                          AND %s = ?
                          AND processed_at IS NULL
                          AND raw_written_at IS NULL
                        """,
                tenantId, workspaceId, eventType, eventId, idempotencyKey, 0);
    }

    @Transactional
    public boolean registerIfNew(String tenantId,
                                 String workspaceId,
                                 String eventType,
                                 String eventId,
                                 String idempotencyKey) {
        return claimIfNew(tenantId, workspaceId, eventType, eventId, idempotencyKey);
    }

    private int updateClaim(String sqlTemplate,
                            String tenantId,
                            String workspaceId,
                            String eventType,
                            String eventId,
                            String idempotencyKey,
                            int timestampParameterCount) {
        ClaimKey claimKey = claimKey(eventId, idempotencyKey);
        ExistingClaim existingClaim = findExistingClaim(tenantId, workspaceId, eventType, claimKey)
                .orElseGet(() -> new ExistingClaim(null, claimKey.lookupColumn(), claimKey.lookupValue()));
        var query = entityManager.createNativeQuery(String.format(sqlTemplate, existingClaim.lookupColumn()));
        int parameter = 1;
        if (timestampParameterCount > 0) {
            Instant now = Instant.now();
            for (int i = 0; i < timestampParameterCount; i++) {
                query.setParameter(parameter++, now);
            }
        }
        query.setParameter(parameter++, tenantId)
                .setParameter(parameter++, workspaceId)
                .setParameter(parameter++, eventType)
                .setParameter(parameter, existingClaim.lookupValue());
        return query.executeUpdate();
    }

    private boolean insertClaim(String tenantId,
                                String workspaceId,
                                String eventType,
                                ClaimKey claimKey) {
        try {
            Instant now = Instant.now();
            int rows = entityManager.createNativeQuery("""
                    INSERT INTO tracking_event_idempotency
                    (id, tenant_id, workspace_id, event_type, event_id, idempotency_key,
                     raw_written_at, processed_at, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, 0)
                    ON CONFLICT DO NOTHING
                    """)
                    .setParameter(1, UUID.randomUUID().toString())
                    .setParameter(2, tenantId)
                    .setParameter(3, workspaceId)
                    .setParameter(4, eventType)
                    .setParameter(5, claimKey.insertEventId())
                    .setParameter(6, claimKey.idempotencyKey())
                    .setParameter(7, now)
                    .setParameter(8, now)
                    .executeUpdate();
            return rows == 1;
        } catch (Exception e) {
            if (isDuplicateKey(e)) {
                return false;
            }
            throw e;
        }
    }

    private boolean reclaimStaleClaim(String tenantId,
                                      String workspaceId,
                                      String eventType,
                                      ExistingClaim claim) {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(effectiveStaleClaimAge());
        int rows = entityManager.createNativeQuery(String.format("""
                        UPDATE tracking_event_idempotency
                        SET updated_at = ?, version = version + 1
                        WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                          AND %s = ?
                          AND raw_written_at IS NULL
                          AND processed_at IS NULL
                          AND updated_at < ?
                        """, claim.lookupColumn()))
                .setParameter(1, now)
                .setParameter(2, tenantId)
                .setParameter(3, workspaceId)
                .setParameter(4, eventType)
                .setParameter(5, claim.lookupValue())
                .setParameter(6, staleBefore)
                .executeUpdate();
        return rows == 1;
    }

    private Duration effectiveStaleClaimAge() {
        if (staleClaimAge == null || staleClaimAge.isZero() || staleClaimAge.isNegative()) {
            return DEFAULT_STALE_CLAIM_AGE;
        }
        return staleClaimAge;
    }

    private Optional<ExistingClaim> findExistingClaim(String tenantId,
                                                      String workspaceId,
                                                      String eventType,
                                                      ClaimKey claimKey) {
        Optional<ExistingClaim> status = findClaimStatusByColumn(
                tenantId, workspaceId, eventType, "event_id", claimKey.insertEventId());
        if (status.isPresent() || claimKey.idempotencyKey() == null) {
            return status;
        }
        return findClaimStatusByColumn(
                tenantId, workspaceId, eventType, "idempotency_key", claimKey.idempotencyKey());
    }

    @SuppressWarnings("unchecked")
    private Optional<ExistingClaim> findClaimStatusByColumn(String tenantId,
                                                            String workspaceId,
                                                            String eventType,
                                                            String column,
                                                            String value) {
        List<Object> rows = entityManager.createNativeQuery(String.format("""
                        SELECT CASE
                            WHEN processed_at IS NOT NULL THEN 'PROCESSED'
                            WHEN raw_written_at IS NOT NULL THEN 'RAW_WRITTEN'
                            ELSE 'IN_PROGRESS'
                        END
                        FROM tracking_event_idempotency
                        WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                          AND %s = ?
                        LIMIT 1
                        """, column))
                .setParameter(1, tenantId)
                .setParameter(2, workspaceId)
                .setParameter(3, eventType)
                .setParameter(4, value)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ExistingClaim(ClaimStatus.valueOf(String.valueOf(rows.get(0))), column, value));
    }

    private ClaimKey claimKey(String eventId, String idempotencyKey) {
        String normalizedEventId = normalize(eventId);
        String normalizedIdempotencyKey = normalize(idempotencyKey);
        requireClaimKey(normalizedEventId, normalizedIdempotencyKey);
        return new ClaimKey(normalizedEventId, normalizedIdempotencyKey);
    }

    private void requireClaimKey(String eventId, String idempotencyKey) {
        if (eventId == null && idempotencyKey == null) {
            throw new IllegalArgumentException("eventId or idempotencyKey is required for tracking idempotency");
        }
    }

    private boolean isDuplicateKey(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("duplicate key") || normalized.contains("23505");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ClaimKey(String eventId, String idempotencyKey) {

        String insertEventId() {
            return eventId != null ? eventId : idempotencyKey;
        }

        String lookupColumn() {
            return eventId != null ? "event_id" : "idempotency_key";
        }

        String lookupValue() {
            return eventId != null ? eventId : idempotencyKey;
        }
    }

    private record ExistingClaim(ClaimStatus status, String lookupColumn, String lookupValue) {
    }
}
