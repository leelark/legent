package com.legent.deliverability.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "domain_reputations")
@Getter
@Setter
public class DomainReputation {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "domain_id", nullable = false)
    private String domainId;

    @Column(name = "reputation_score")
    private Integer reputationScore = 100;

    @Column(name = "hard_bounce_rate")
    private BigDecimal hardBounceRate = BigDecimal.ZERO;

    @Column(name = "complaint_rate")
    private BigDecimal complaintRate = BigDecimal.ZERO;

    @Column(name = "calculated_at")
    private Instant calculatedAt = Instant.now();
}
