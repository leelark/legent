package com.legent.campaign.service;

import com.legent.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exactly-once guard for campaign-side event handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignEventIdempotencyService {

    private static final long CLAIMED_VERSION = -1L;
    private static final long PROCESSED_VERSION = 0L;
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(15);

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerIfNew(String tenantId,
                                 String workspaceId,
                                 String eventType,
                                 String eventId,
                                 String idempotencyKey) {
        String normalizedEventId = normalize(eventId);
        String normalizedIdempotencyKey = normalize(idempotencyKey);
        if (normalizedEventId == null && normalizedIdempotencyKey == null) {
            return true;
        }

        String sql = """
                INSERT INTO campaign_event_idempotency
                (id, tenant_id, workspace_id, event_type, event_id, idempotency_key, processed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW(), ?)
                ON CONFLICT DO NOTHING
                """;
        int rows = jdbcTemplate.update(
                sql,
                IdGenerator.newId(),
                tenantId,
                workspaceId,
                eventType,
                normalizedEventId,
                normalizedIdempotencyKey,
                CLAIMED_VERSION
        );
        if (rows > 0) {
            return true;
        }

        if (isProcessed(tenantId, workspaceId, eventType, normalizedEventId, normalizedIdempotencyKey)) {
            log.debug("Skipping duplicate campaign event tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                    tenantId, workspaceId, eventType, eventId, idempotencyKey);
            return false;
        }

        int reclaimed = reclaimStaleClaim(tenantId, workspaceId, eventType, normalizedEventId, normalizedIdempotencyKey);
        if (reclaimed > 0) {
            log.warn("Reclaimed stale campaign event claim tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                    tenantId, workspaceId, eventType, eventId, idempotencyKey);
            return true;
        }

        log.debug("Skipping in-flight campaign event tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                tenantId, workspaceId, eventType, eventId, idempotencyKey);
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String tenantId,
                              String workspaceId,
                              String eventType,
                              String eventId,
                              String idempotencyKey) {
        String normalizedEventId = normalize(eventId);
        String normalizedIdempotencyKey = normalize(idempotencyKey);
        if (normalizedEventId == null && normalizedIdempotencyKey == null) {
            return;
        }

        List<Object> params = identityParams(
                tenantId,
                workspaceId,
                eventType,
                normalizedEventId,
                normalizedIdempotencyKey);
        params.add(CLAIMED_VERSION);

        int rows = jdbcTemplate.update("""
                UPDATE campaign_event_idempotency
                SET processed_at = NOW(), updated_at = NOW(), version = 0
                WHERE tenant_id = ?
                  AND workspace_id = ?
                  AND event_type = ?
                  AND %s
                  AND version = ?
                """.formatted(identityPredicate(normalizedEventId, normalizedIdempotencyKey)), params.toArray());

        if (rows == 0) {
            log.warn("Campaign event claim was not marked processed tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                    tenantId, workspaceId, eventType, eventId, idempotencyKey);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(String tenantId,
                             String workspaceId,
                             String eventType,
                             String eventId,
                             String idempotencyKey) {
        String normalizedEventId = normalize(eventId);
        String normalizedIdempotencyKey = normalize(idempotencyKey);
        if (normalizedEventId == null && normalizedIdempotencyKey == null) {
            return;
        }

        List<Object> params = identityParams(
                tenantId,
                workspaceId,
                eventType,
                normalizedEventId,
                normalizedIdempotencyKey);
        params.add(CLAIMED_VERSION);

        jdbcTemplate.update("""
                DELETE FROM campaign_event_idempotency
                WHERE tenant_id = ?
                  AND workspace_id = ?
                  AND event_type = ?
                  AND %s
                  AND version = ?
                """.formatted(identityPredicate(normalizedEventId, normalizedIdempotencyKey)), params.toArray());
    }

    private boolean isProcessed(String tenantId,
                                String workspaceId,
                                String eventType,
                                String eventId,
                                String idempotencyKey) {
        List<Object> params = identityParams(tenantId, workspaceId, eventType, eventId, idempotencyKey);
        List<Long> versions = jdbcTemplate.query(
                """
                SELECT version
                FROM campaign_event_idempotency
                WHERE tenant_id = ?
                  AND workspace_id = ?
                  AND event_type = ?
                  AND %s
                LIMIT 1
                """.formatted(identityPredicate(eventId, idempotencyKey)),
                (rs, rowNum) -> rs.getLong("version"),
                params.toArray());
        return versions.stream().anyMatch(version -> version >= PROCESSED_VERSION);
    }

    private int reclaimStaleClaim(String tenantId,
                                  String workspaceId,
                                  String eventType,
                                  String eventId,
                                  String idempotencyKey) {
        List<Object> params = identityParams(tenantId, workspaceId, eventType, eventId, idempotencyKey);
        params.add(CLAIMED_VERSION);
        params.add(Timestamp.from(Instant.now().minus(CLAIM_TIMEOUT)));

        return jdbcTemplate.update("""
                UPDATE campaign_event_idempotency
                SET updated_at = NOW()
                WHERE tenant_id = ?
                  AND workspace_id = ?
                  AND event_type = ?
                  AND %s
                  AND version = ?
                  AND updated_at < ?
                """.formatted(identityPredicate(eventId, idempotencyKey)), params.toArray());
    }

    private List<Object> identityParams(String tenantId,
                                        String workspaceId,
                                        String eventType,
                                        String eventId,
                                        String idempotencyKey) {
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(workspaceId);
        params.add(eventType);
        if (eventId != null) {
            params.add(eventId);
        }
        if (idempotencyKey != null) {
            params.add(idempotencyKey);
        }
        return params;
    }

    private String identityPredicate(String eventId, String idempotencyKey) {
        if (eventId != null && idempotencyKey != null) {
            return "(event_id = ? OR idempotency_key = ?)";
        }
        if (eventId != null) {
            return "event_id = ?";
        }
        return "idempotency_key = ?";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
