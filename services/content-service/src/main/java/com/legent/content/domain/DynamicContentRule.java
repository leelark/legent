package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dynamic_content_rules")
@Getter
@Setter
public class DynamicContentRule extends TenantAwareEntity {
    @Column(name = "workspace_id", length = 36)
    private String workspaceId;

    @Column(name = "template_id", nullable = false, length = 36)
    private String templateId;

    @Column(name = "slot_key", nullable = false, length = 128)
    private String slotKey = "main";

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer priority = 100;

    @Column(name = "condition_field", length = 128)
    private String conditionField;

    @Column(nullable = false, length = 32)
    private String operator = "ALWAYS";

    @Column(name = "condition_value", columnDefinition = "TEXT")
    private String conditionValue;

    @Column(name = "html_content", columnDefinition = "TEXT")
    private String htmlContent;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(nullable = false)
    private Boolean active = true;
}
