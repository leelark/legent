package com.legent.audience.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Column definition for a data extension.
 */
@Entity
@Table(name = "data_extension_fields")
@Getter
@Setter
@NoArgsConstructor
public class DataExtensionField extends BaseEntity {

    @Column(name = "data_extension_id", nullable = false, length = 26)
    private String dataExtensionId;

    @Column(name = "field_name", nullable = false, length = 128)
    private String fieldName;

    @Column(name = "field_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FieldType fieldType = FieldType.TEXT;

    @Column(name = "is_required", nullable = false)
    private boolean required = false;

    @Column(name = "is_primary_key", nullable = false)
    private boolean primaryKey = false;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "ordinal", nullable = false)
    private int ordinal = 0;

    public enum FieldType {
        TEXT, NUMBER, DECIMAL, BOOLEAN, DATE, DATETIME, EMAIL, PHONE, LOCALE
    }
}
