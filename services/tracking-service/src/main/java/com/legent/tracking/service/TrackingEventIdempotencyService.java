package com.legent.tracking.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class TrackingEventIdempotencyService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public boolean claimIfNew(String tenantId,
                              String workspaceId,
                              String eventType,
                              String eventId,
                              String idempotencyKey) {
        String normalizedEventId = normalize(eventId);
        String normalizedIdempotencyKey = normalize(idempotencyKey);
        requireClaimKey(normalizedEventId, normalizedIdempotencyKey);
        if (normalizedEventId == null) {
            normalizedEventId = normalizedIdempotencyKey;
        }
        try {
            int rows = entityManager.createNativeQuery("""
                    INSERT INTO tracking_event_idempotency
                    (id, tenant_id, workspace_id, event_type, event_id, idempotency_key, processed_at, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, 0)
                    ON CONFLICT DO NOTHING
                    """)
                    .setParameter(1, UUID.randomUUID().toString())
                    .setParameter(2, tenantId)
                    .setParameter(3, workspaceId)
                    .setParameter(4, eventType)
                    .setParameter(5, normalizedEventId)
                    .setParameter(6, normalizedIdempotencyKey)
                    .setParameter(7, Instant.now())
                    .setParameter(8, Instant.now())
                    .executeUpdate();
            if (rows == 0) {
                log.debug("Skipping duplicate tracking event tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                        tenantId, workspaceId, eventType, eventId, idempotencyKey);
                return false;
            }
            return true;
        } catch (Exception e) {
            if (isDuplicateKey(e)) {
                log.debug("Skipping duplicate tracking event tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                        tenantId, workspaceId, eventType, eventId, idempotencyKey);
                return false;
            }
            throw e;
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
                        SET processed_at = ?, updated_at = ?, version = version + 1
                        WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                          AND %s = ?
                          AND processed_at IS NULL
                        """,
                tenantId, workspaceId, eventType, eventId, idempotencyKey, true);
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
                        """,
                tenantId, workspaceId, eventType, eventId, idempotencyKey, false);
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
                            boolean includeTimestamps) {
        String normalizedEventId = normalize(eventId);
        String normalizedIdempotencyKey = normalize(idempotencyKey);
        requireClaimKey(normalizedEventId, normalizedIdempotencyKey);

        String column;
        String value;
        if (normalizedEventId != null) {
            column = "event_id";
            value = normalizedEventId;
        } else {
            column = "idempotency_key";
            value = normalizedIdempotencyKey;
        }

        var query = entityManager.createNativeQuery(String.format(sqlTemplate, column));
        if (includeTimestamps) {
            Instant now = Instant.now();
            query.setParameter(1, now)
                    .setParameter(2, now)
                    .setParameter(3, tenantId)
                    .setParameter(4, workspaceId)
                    .setParameter(5, eventType)
                    .setParameter(6, value);
        } else {
            query.setParameter(1, tenantId)
                    .setParameter(2, workspaceId)
                    .setParameter(3, eventType)
                    .setParameter(4, value);
        }
        return query.executeUpdate();
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
}
