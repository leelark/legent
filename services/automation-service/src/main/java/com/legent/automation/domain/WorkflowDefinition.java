package com.legent.automation.domain;

import java.time.Instant;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "workflow_definitions")
@Getter
@Setter
@IdClass(WorkflowDefinition.WorkflowDefinitionId.class)
public class WorkflowDefinition {

    @Id
    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @Id
    @Column(nullable = false)
    private Integer version;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String definition; // The serialized JSON Node Map

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Getter
    @Setter
    public static class WorkflowDefinitionId implements Serializable {
        private String workflowId;
        private Integer version;
    }
}
