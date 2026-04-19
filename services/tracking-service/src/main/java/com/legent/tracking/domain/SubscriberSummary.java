package com.legent.tracking.domain;

import java.time.Instant;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "subscriber_summaries")
@Getter
@Setter
public class SubscriberSummary extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "subscriber_id", nullable = false)
    private String subscriberId;

    @Column(name = "total_received")
    private Long totalReceived = 0L;

    @Column(name = "total_opens")
    private Long totalOpens = 0L;

    @Column(name = "total_clicks")
    private Long totalClicks = 0L;

    @Column(name = "last_engaged_at")
    private Instant lastEngagedAt;
}
