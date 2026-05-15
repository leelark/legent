package com.legent.automation.service;

import com.legent.common.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exactly-once guard for automation event ingestion and execution emissions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationEventIdempotencyService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public boolean claimIfNew(String tenantId,
                              String workspaceId,
                              String eventType,
                              String eventId,
                              String idempotencyKey) {
        String normalizedEventId = normalize(eventId);
        String normalizedIdempotencyKey = normalize(idempotencyKey);
        requireClaimKey(normalizedEventId, normalizedIdempotencyKey);

        String sql = """
                INSERT INTO automation_event_idempotency
                (id, tenant_id, workspace_id, event_type, event_id, idempotency_key, processed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, NULL, NOW(), NOW(), 0)
                ON CONFLICT DO NOTHING
                """;
        int rows = jdbcTemplate.update(
                sql,
                IdGenerator.newId(),
                tenantId,
                workspaceId,
                eventType,
                normalizedEventId,
                normalizedIdempotencyKey
        );
        if (rows == 0) {
            log.debug("Skipping duplicate automation event tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                    tenantId, workspaceId, eventType, eventId, idempotencyKey);
            return false;
        }
        return true;
    }

    @Transactional
    public void markProcessed(String tenantId,
                              String workspaceId,
                              String eventType,
                              String eventId,
                              String idempotencyKey) {
        int rows = updateClaim("""
                        UPDATE automation_event_idempotency
                        SET processed_at = NOW(), updated_at = NOW(), version = version + 1
                        WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                          AND %s = ?
                          AND processed_at IS NULL
                        """,
                tenantId, workspaceId, eventType, eventId, idempotencyKey);
        if (rows != 1) {
            throw new IllegalStateException("Unable to mark automation event idempotency claim processed");
        }
    }

    @Transactional
    public void releaseClaim(String tenantId,
                             String workspaceId,
                             String eventType,
                             String eventId,
                             String idempotencyKey) {
        updateClaim("""
                        DELETE FROM automation_event_idempotency
                        WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                          AND %s = ?
                          AND processed_at IS NULL
                        """,
                tenantId, workspaceId, eventType, eventId, idempotencyKey);
    }

    @Transactional
    public boolean registerIfNew(String tenantId,
                                 String workspaceId,
                                 String eventType,
                                 String eventId,
                                 String idempotencyKey) {
        if (!claimIfNew(tenantId, workspaceId, eventType, eventId, idempotencyKey)) {
            return false;
        }
        markProcessed(tenantId, workspaceId, eventType, eventId, idempotencyKey);
        return true;
    }

    private int updateClaim(String sqlTemplate,
                            String tenantId,
                            String workspaceId,
                            String eventType,
                            String eventId,
                            String idempotencyKey) {
        String normalizedEventId = normalize(eventId);
        String normalizedIdempotencyKey = normalize(idempotencyKey);
        requireClaimKey(normalizedEventId, normalizedIdempotencyKey);
        if (normalizedEventId != null) {
            return jdbcTemplate.update(String.format(sqlTemplate, "event_id"),
                    tenantId, workspaceId, eventType, normalizedEventId);
        }
        return jdbcTemplate.update(String.format(sqlTemplate, "idempotency_key"),
                tenantId, workspaceId, eventType, normalizedIdempotencyKey);
    }

    private void requireClaimKey(String eventId, String idempotencyKey) {
        if (eventId == null && idempotencyKey == null) {
            throw new IllegalArgumentException("eventId or idempotencyKey is required for automation idempotency");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
