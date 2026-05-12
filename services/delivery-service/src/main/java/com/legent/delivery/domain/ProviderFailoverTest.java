package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "provider_failover_tests")
@Getter
@Setter
public class ProviderFailoverTest extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "primary_provider_id", nullable = false, length = 64)
    private String primaryProviderId;

    @Column(name = "failover_provider_id", length = 64)
    private String failoverProviderId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "result_code", nullable = false, length = 64)
    private String resultCode;

    @Column(name = "diagnostic", length = 2000)
    private String diagnostic;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
