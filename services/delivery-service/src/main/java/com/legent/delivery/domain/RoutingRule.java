package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "routing_rules")
@Getter
@Setter
public class RoutingRule extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "sender_domain", nullable = false)
    private String senderDomain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private SmtpProvider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ip_pool_id")
    private IpPool ipPool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fallback_provider_id")
    private SmtpProvider fallbackProvider;

    @Column(name = "is_active")
    private boolean isActive = true;
}
