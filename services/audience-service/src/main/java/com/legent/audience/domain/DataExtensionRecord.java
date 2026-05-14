package com.legent.audience.domain;

import java.util.Map;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


/**
 * Dynamic data extension record — stores row data as JSONB.
 */
@Entity
@Table(name = "data_extension_records")
@Getter
@Setter
@NoArgsConstructor
public class DataExtensionRecord extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "data_extension_id", nullable = false, length = 36)
    private String dataExtensionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "record_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> recordData;
}
