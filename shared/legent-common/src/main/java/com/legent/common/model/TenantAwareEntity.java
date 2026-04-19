package com.legent.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

/**
 * Base entity for multi-tenant tables.
 * Adds a mandatory tenant_id column that is set automatically
 * from the TenantContext during persistence.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 26, nullable = false, updatable = false)
    private String tenantId;

    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (this.tenantId == null) {
            throw new IllegalStateException(
                "tenantId must be set before persisting a tenant-aware entity"
            );
        }
    }
}
