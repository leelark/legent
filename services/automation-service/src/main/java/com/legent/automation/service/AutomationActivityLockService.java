package com.legent.automation.service;

import com.legent.common.util.IdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AutomationActivityLockService {

    private final NamedParameterJdbcTemplate jdbc;

    public AutomationActivityLockService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public LockLease acquire(String tenantId,
                             String workspaceId,
                             String activityId,
                             String runId,
                             boolean operatorOverride,
                             String overrideReason,
                             Duration ttl) {
        Instant now = Instant.now();
        Instant lockedUntil = now.plus(ttl == null ? Duration.ofMinutes(15) : ttl);
        Map<String, Object> queryParams = Map.of(
                "tenantId", tenantId,
                "workspaceId", workspaceId,
                "activityId", activityId,
                "now", now);
        Map<String, Object> active = activeLock(queryParams);
        if (active != null && !operatorOverride) {
            return locked(active, now, "Automation activity is already locked by another live run.");
        }
        if (active != null && blank(overrideReason) == null) {
            return locked(active, now, "Operator override requires a nonblank reason.");
        }
        if (active != null) {
            jdbc.update("""
                    UPDATE automation_activity_locks
                    SET status = 'OVERRIDDEN',
                        released_at = :now,
                        heartbeat_at = :now,
                        updated_at = :now,
                        version = version + 1
                    WHERE id = :id
                      AND tenant_id = :tenantId
                      AND workspace_id = :workspaceId
                      AND activity_id = :activityId
                      AND status = 'ACTIVE'
                    """, Map.of(
                    "id", active.get("id"),
                    "tenantId", tenantId,
                    "workspaceId", workspaceId,
                    "activityId", activityId,
                    "now", now));
        }
        try {
            String lockId = IdGenerator.newId();
            Map<String, Object> insertParams = new LinkedHashMap<>();
            insertParams.put("id", lockId);
            insertParams.put("tenantId", tenantId);
            insertParams.put("workspaceId", workspaceId);
            insertParams.put("activityId", activityId);
            insertParams.put("runId", runId);
            insertParams.put("lockedUntil", lockedUntil);
            insertParams.put("lockOwner", operatorOverride ? "OPERATOR_OVERRIDE" : "RUN");
            insertParams.put("overrideReason", blank(overrideReason));
            insertParams.put("now", now);
            jdbc.update("""
                    INSERT INTO automation_activity_locks (
                        id, tenant_id, workspace_id, activity_id, run_id, status,
                        locked_until, lock_owner, override_reason, acquired_at,
                        heartbeat_at, created_at, updated_at, version
                    ) VALUES (
                        :id, :tenantId, :workspaceId, :activityId, :runId, 'ACTIVE',
                        :lockedUntil, :lockOwner, :overrideReason, :now,
                        :now, :now, :now, 0
                    )
                    """, insertParams);
            return LockLease.acquired(lockId, runId, lockedUntil, operatorOverride, blank(overrideReason));
        } catch (DuplicateKeyException ex) {
            Map<String, Object> winner = activeLock(queryParams);
            if (winner != null) {
                return locked(winner, now, "Automation activity lock was acquired concurrently.");
            }
            throw ex;
        }
    }

    @Transactional
    public void release(String tenantId, String workspaceId, String activityId, String runId) {
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE automation_activity_locks
                SET status = 'RELEASED',
                    released_at = :now,
                    heartbeat_at = :now,
                    updated_at = :now,
                    version = version + 1
                WHERE tenant_id = :tenantId
                  AND workspace_id = :workspaceId
                  AND activity_id = :activityId
                  AND run_id = :runId
                  AND status = 'ACTIVE'
                """, Map.of(
                "tenantId", tenantId,
                "workspaceId", workspaceId,
                "activityId", activityId,
                "runId", runId,
                "now", now));
    }

    private Map<String, Object> activeLock(Map<String, Object> params) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT *
                FROM automation_activity_locks
                WHERE tenant_id = :tenantId
                  AND workspace_id = :workspaceId
                  AND activity_id = :activityId
                  AND status = 'ACTIVE'
                  AND locked_until > :now
                  AND deleted_at IS NULL
                ORDER BY locked_until DESC
                LIMIT 1
                """, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private LockLease locked(Map<String, Object> row, Instant now, String reason) {
        Instant lockedUntil = instant(row.get("locked_until"));
        long retryAfter = lockedUntil == null ? 60L : Math.max(1L, Duration.between(now, lockedUntil).toSeconds());
        return LockLease.locked(
                String.valueOf(row.get("id")),
                String.valueOf(row.get("run_id")),
                lockedUntil,
                retryAfter,
                reason);
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value != null) {
            return Instant.parse(String.valueOf(value));
        }
        return null;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record LockLease(
            boolean acquired,
            String lockId,
            String runId,
            Instant lockedUntil,
            Long retryAfterSeconds,
            String reason,
            boolean operatorOverride,
            String overrideReason) {

        public static LockLease acquired(String lockId,
                                         String runId,
                                         Instant lockedUntil,
                                         boolean operatorOverride,
                                         String overrideReason) {
            return new LockLease(true, lockId, runId, lockedUntil, null, null, operatorOverride, overrideReason);
        }

        public static LockLease locked(String lockId,
                                       String ownerRunId,
                                       Instant lockedUntil,
                                       Long retryAfterSeconds,
                                       String reason) {
            return new LockLease(false, lockId, ownerRunId, lockedUntil, retryAfterSeconds, reason, false, null);
        }
    }
}
