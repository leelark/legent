package com.legent.campaign.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "throttling_rules")
@Getter
@Setter
public class ThrottlingRule extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String domain;

    @Column(name = "max_emails", nullable = false)
    private Integer maxEmails;

    @Column(name = "time_window_seconds", nullable = false)
    private Integer timeWindowSeconds;

    @Column(nullable = false)
    private boolean enabled = true;
}
