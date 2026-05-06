package com.legent.automation.repository;

import java.util.Optional;

import com.legent.automation.domain.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {
    Optional<WorkflowInstance> findByIdAndTenantIdAndWorkspaceId(String id, String tenantId, String workspaceId);
    java.util.List<WorkflowInstance> findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByCreatedAtDesc(String tenantId, String workspaceId, String workflowId);
}
