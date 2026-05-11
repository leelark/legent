package com.legent.automation.domain;

import com.legent.automation.dto.AutomationStudioDto;
import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "automation_activity_runs")
@Getter
@Setter
public class AutomationActivityRun extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "activity_id", nullable = false, length = 26)
    private String activityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AutomationStudioDto.RunStatus status = AutomationStudioDto.RunStatus.VERIFIED;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = true;

    @Column(name = "trigger_source", length = 64)
    private String triggerSource;

    @Column(name = "rows_read")
    private Long rowsRead = 0L;

    @Column(name = "rows_written")
    private Long rowsWritten = 0L;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson = "{}";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;
}
