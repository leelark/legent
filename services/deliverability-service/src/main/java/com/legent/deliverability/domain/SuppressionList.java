package com.legent.deliverability.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "suppression_list")
@Getter
@Setter
public class SuppressionList {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String reason; // HARD_BOUNCE, COMPLAINT, UNSUBSCRIBE

    private String source;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
