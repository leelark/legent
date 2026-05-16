package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "render_validation_reports")
@Getter
@Setter
public class RenderValidationReport extends TenantAwareEntity {
    @Column(name = "workspace_id", length = 36)
    private String workspaceId;

    @Column(name = "template_id", length = 36)
    private String templateId;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_json", columnDefinition = "JSONB", nullable = false)
    private String reportJson = "{}";
}
