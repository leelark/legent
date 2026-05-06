package com.legent.automation.domain;

import com.legent.common.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;

@Entity
@Table(name = "workflow_schedules")
@Getter
@Setter
public class WorkflowSchedule extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @Column(name = "schedule_type", nullable = false)
    private String scheduleType = "CRON";

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(name = "timezone", nullable = false)
    private String timezone = "UTC";

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata = "{}";
}
