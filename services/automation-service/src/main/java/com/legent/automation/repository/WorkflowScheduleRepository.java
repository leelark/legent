package com.legent.automation.repository;

import com.legent.automation.domain.WorkflowSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowScheduleRepository extends JpaRepository<WorkflowSchedule, String> {
    List<WorkflowSchedule> findByTenantIdAndWorkspaceIdAndWorkflowIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, String workspaceId, String workflowId);
    Optional<WorkflowSchedule> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);
}
