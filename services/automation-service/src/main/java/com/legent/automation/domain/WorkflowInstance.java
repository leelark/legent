package com.legent.automation.domain;

import java.time.Instant;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "workflow_instances")
@Getter
@Setter
public class WorkflowInstance {

    @Id
    private String id; // Pre-generated ULID

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "subscriber_id", nullable = false)
    private String subscriberId;

    @Column(nullable = false)
    private String status = "RUNNING"; // RUNNING, WAITING, COMPLETED, FAILED

    @Column(name = "current_node_id")
    private String currentNodeId;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private String context; // Current variables in memory for this subscriber

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
