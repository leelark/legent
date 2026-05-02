package com.legent.foundation.domain;

import java.time.Instant;
import java.util.Map;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


/**
 * Tenant entity — top-level multi-tenancy unit.
 * Global entity (no tenant_id on itself).
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 128)
    private String slug;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "plan", nullable = false, length = 50)
    private String plan = "STARTER";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "branding", columnDefinition = "jsonb")
    private Map<String, Object> branding;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public enum TenantStatus {
        ACTIVE, SUSPENDED, PROVISIONING, DEACTIVATED, ARCHIVED
    }
}
