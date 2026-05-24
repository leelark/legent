package com.legent.audience.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * First-class metadata contract for data-extension relationships.
 */
@Entity
@Table(name = "data_extension_relationships")
@Getter
@Setter
@NoArgsConstructor
public class DataExtensionRelationship extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "source_data_extension_id", nullable = false, length = 36)
    private String sourceDataExtensionId;

    @Column(name = "target_data_extension_id", nullable = false, length = 36)
    private String targetDataExtensionId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "source_field", nullable = false, length = 128)
    private String sourceField;

    @Column(name = "target_field", nullable = false, length = 128)
    private String targetField;

    @Column(name = "cardinality", nullable = false, length = 32)
    private String cardinality;

    @Column(name = "is_required", nullable = false)
    private boolean required = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "ordinal", nullable = false)
    private int ordinal = 0;
}
