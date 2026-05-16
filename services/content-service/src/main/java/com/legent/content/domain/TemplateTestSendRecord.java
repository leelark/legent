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
@Table(name = "template_test_send_records")
@Getter
@Setter
public class TemplateTestSendRecord extends TenantAwareEntity {
    @Column(name = "workspace_id", length = 36)
    private String workspaceId;

    @Column(name = "template_id", nullable = false, length = 36)
    private String templateId;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(name = "recipient_group")
    private String recipientGroup;

    @Column(length = 500)
    private String subject;

    @Column(nullable = false, length = 32)
    private String status = "QUEUED";

    @Column(name = "message_id")
    private String messageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_json", columnDefinition = "JSONB")
    private String variablesJson = "{}";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
