package com.legent.tracking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getEventCounts(String tenantId, String workspaceId) {
        if (tenantId == null || tenantId.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            log.warn("Tenant/workspace context missing; returning empty event counts");
            return new ArrayList<>();
        }
        try {
            return jdbcTemplate.queryForList("""
                SELECT event_type, COALESCE(count(*), 0) AS count
                FROM raw_events
                WHERE tenant_id = ? AND workspace_id = ?
                GROUP BY event_type
            """, tenantId, workspaceId);
        } catch (DataAccessException e) {
            log.error("Failed to query event counts for tenant {} workspace {}", tenantId, workspaceId, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getEventTimeline(String tenantId, String workspaceId, String eventType) {
        if (tenantId == null || tenantId.isBlank()
                || workspaceId == null || workspaceId.isBlank()
                || eventType == null || eventType.isBlank()) {
            log.warn("Tenant/workspace/eventType missing; returning empty timeline");
            return new ArrayList<>();
        }
        String normalizedEventType = eventType.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return jdbcTemplate.queryForList("""
                SELECT date_trunc('hour', "timestamp") AS hour, COALESCE(count(*), 0) AS count
                FROM raw_events
                WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                GROUP BY hour
                ORDER BY hour
            """, tenantId, workspaceId, normalizedEventType);
        } catch (DataAccessException e) {
            log.error("Failed to query event timeline for tenant {} workspace {} and eventType {}",
                    tenantId, workspaceId, eventType, e);
            return new ArrayList<>();
        }
    }
}
