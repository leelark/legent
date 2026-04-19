package com.legent.audience.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data extension schema definition.
 * is_sendable = true means this DE can be used as a send audience.
 */
@Entity
@Table(name = "data_extensions")
@Getter
@Setter
@NoArgsConstructor
public class DataExtension extends TenantAwareEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "is_sendable", nullable = false)
    private boolean sendable = false;

    @Column(name = "sendable_field", length = 128)
    private String sendableField;

    @Column(name = "primary_key_field", length = 128)
    private String primaryKeyField;

    @Column(name = "record_count", nullable = false)
    private long recordCount = 0;
}
