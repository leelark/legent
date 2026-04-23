package com.legent.foundation.domain;

import java.util.List;

import java.util.Map;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


/**
 * Feature flag entity.
 * Supports global flags (tenant_id = null) and tenant-specific overrides.
 * The 'rules' JSONB column supports conditional targeting.
 */
@Entity
@Table(name = "feature_flags")
@Getter
@Setter
@NoArgsConstructor
public class FeatureFlag extends BaseEntity {

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "flag_key", nullable = false, length = 128)
    private String flagKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "scope", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FlagScope scope = FlagScope.GLOBAL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules", columnDefinition = "jsonb")
    private List<Map<String, Object>> rules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    public enum FlagScope {
        GLOBAL, TENANT, ENVIRONMENT
    }
}
