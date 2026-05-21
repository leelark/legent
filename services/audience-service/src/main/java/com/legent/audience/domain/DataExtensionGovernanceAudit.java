package com.legent.audience.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "data_extension_governance_audit")
@Getter
@Setter
@NoArgsConstructor
public class DataExtensionGovernanceAudit extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "data_extension_id", nullable = false, length = 36)
    private String dataExtensionId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "summary", nullable = false, length = 1000)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
