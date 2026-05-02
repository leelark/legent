package com.legent.tracking.service;

import com.legent.tracking.dto.TrackingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickHouseWriter {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public void writeBatch(List<TrackingDto.RawEventPayload> events) {
        String sql = "INSERT INTO raw_events (id, tenant_id, event_type, campaign_id, subscriber_id, message_id, user_agent, ip_address, link_url, timestamp, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        List<Object[]> batchArgs = events.stream()
                .map(event -> new Object[]{
                        event.getId(),
                        event.getTenantId(),
                        event.getEventType() == null ? null : event.getEventType().trim().toUpperCase(java.util.Locale.ROOT),
                        event.getCampaignId(),
                        event.getSubscriberId(),
                        event.getMessageId(),
                        event.getUserAgent(),
                        event.getIpAddress(),
                        event.getLinkUrl(),
                        Timestamp.from(event.getTimestamp() == null ? java.time.Instant.now() : event.getTimestamp()),
                        serializeMetadata(event.getMetadata())
                })
                .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(sql, java.util.Objects.requireNonNull(batchArgs));
        log.info("Successfully wrote batch of {} events to ClickHouse", events.size());
    }

    private String serializeMetadata(java.util.Map<String, Object> metadata) {
        if (metadata == null) return "{}";
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }
}
