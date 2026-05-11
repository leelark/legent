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
@Table(name = "automation_activities")
@Getter
@Setter
public class AutomationActivity extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 32)
    private AutomationStudioDto.ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AutomationStudioDto.ActivityStatus status = AutomationStudioDto.ActivityStatus.DRAFT;

    @Column(name = "schedule_expression", length = 128)
    private String scheduleExpression;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_config", columnDefinition = "jsonb")
    private String inputConfig = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_config", columnDefinition = "jsonb")
    private String outputConfig = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_json", columnDefinition = "jsonb")
    private String verificationJson = "{}";

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;
}
