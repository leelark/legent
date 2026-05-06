package com.legent.tracking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FunnelService {
    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getFunnel(String tenantId, String workspaceId, String campaignId) {
        // Example: open, click, conversion counts for a campaign
        return jdbcTemplate.queryForList("""
            SELECT event_type, count(*) AS count
            FROM raw_events
            WHERE tenant_id = ? AND workspace_id = ? AND campaign_id = ?
            GROUP BY event_type
        """, tenantId, workspaceId, campaignId);
    }
}
