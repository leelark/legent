package com.legent.tracking.domain;

import java.time.Instant;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "raw_events")
@Getter
@Setter
public class RawEvent {

    @Id
    private String id; // Pre-generated ULID

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "event_type", nullable = false)
    private String eventType; // OPEN, CLICK, CONVERSION, BOUNCE, COMPLAINT

    @Column(name = "campaign_id")
    private String campaignId;

    @Column(name = "subscriber_id")
    private String subscriberId;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "link_url")
    private String linkUrl;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private String metadata; // Standardized JSON string for extra fields
}
