package com.legent.audience.domain;

import java.time.Instant;
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
@Table(name = "contact_lifecycle_audit")
@Getter
@Setter
@NoArgsConstructor
public class ContactLifecycleAudit extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "subject_type", nullable = false, length = 40)
    private String subjectType;

    @Column(name = "subject_id", length = 36)
    private String subjectId;

    @Column(name = "subscriber_id", length = 36)
    private String subscriberId;

    @Column(name = "data_extension_id", length = 36)
    private String dataExtensionId;

    @Column(name = "email_sha256", length = 64)
    private String emailSha256;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "outcome", nullable = false, length = 40)
    private String outcome;

    @Column(name = "source", nullable = false, length = 80)
    private String source;

    @Column(name = "source_event_id", length = 120)
    private String sourceEventId;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "operation_id", length = 120)
    private String operationId;

    @Column(name = "performed_by", length = 36)
    private String performedBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
