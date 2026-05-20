package com.legent.platform.service;

import com.legent.common.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformEventIdempotencyService {

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
                INSERT INTO platform_event_idempotency
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
                normalizedIdempotencyKey);
        if (rows == 0) {
            log.debug("Skipping duplicate platform event tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                    tenantId, workspaceId, eventType, normalizedEventId, normalizedIdempotencyKey);
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
                        UPDATE platform_event_idempotency
                        SET processed_at = NOW(), updated_at = NOW(), version = version + 1
                        WHERE tenant_id = ? AND workspace_id IS NOT DISTINCT FROM ? AND event_type = ?
                          AND %s = ?
                          AND processed_at IS NULL
                        """,
                tenantId, workspaceId, eventType, eventId, idempotencyKey);
        if (rows != 1) {
            throw new IllegalStateException("Unable to mark platform event idempotency claim processed");
        }
    }

    @Transactional
    public void releaseClaim(String tenantId,
                             String workspaceId,
                             String eventType,
                             String eventId,
                             String idempotencyKey) {
        updateClaim("""
                        DELETE FROM platform_event_idempotency
                        WHERE tenant_id = ? AND workspace_id IS NOT DISTINCT FROM ? AND event_type = ?
                          AND %s = ?
                          AND processed_at IS NULL
                        """,
                tenantId, workspaceId, eventType, eventId, idempotencyKey);
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
            throw new IllegalArgumentException("eventId or idempotencyKey is required for platform idempotency");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
