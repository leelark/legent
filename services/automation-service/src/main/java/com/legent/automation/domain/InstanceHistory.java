package com.legent.automation.domain;

import java.time.Instant;

import com.legent.common.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "instance_history")
@Getter
@Setter
public class InstanceHistory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "instance_id", nullable = false)
    private String instanceId;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(nullable = false)
    private String status; // SUCCESS, ERROR

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "correlation_id")
    private String correlationId;

    @Type(JsonBinaryType.class)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "executed_at", insertable = false, updatable = false)
    private Instant executedAt;
}
