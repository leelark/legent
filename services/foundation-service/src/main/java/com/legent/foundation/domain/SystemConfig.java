package com.legent.foundation.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * System configuration entity.
 * Supports global configs (tenant_id = null) and tenant-level overrides.
 */
@Entity
@Table(name = "system_configs")
@Getter
@Setter
@NoArgsConstructor
public class SystemConfig extends BaseEntity {

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "value_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ValueType valueType = ValueType.STRING;

    @Column(name = "category", nullable = false, length = 64)
    private String category = "GENERAL";

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_encrypted", nullable = false)
    private boolean encrypted = false;

    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    @Column(name = "config_version", nullable = false)
    private Integer configVersion = 1;

    @Column(name = "last_modified_by", length = 36)
    private String lastModifiedBy;

    public enum ValueType {
        STRING, INTEGER, BOOLEAN, JSON, DECIMAL
    }
}
