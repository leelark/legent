package com.legent.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Extends TenantAwareEntity with an updatedBy field
 * for full audit trail support.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AuditableEntity extends TenantAwareEntity {

    @Column(name = "updated_by", length = 26)
    private String updatedBy;
}
