package com.legent.automation.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "workflows")
@Getter
@Setter
public class Workflow extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "team_id")
    private String teamId;

    @Column(name = "ownership_scope", nullable = false)
    private String ownershipScope = "WORKSPACE";

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT, ACTIVE, PAUSED, SCHEDULED, STOPPED, ARCHIVED, FAILED, ROLLED_BACK

    @Column(name = "active_definition_version")
    private Integer activeDefinitionVersion = 1;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
