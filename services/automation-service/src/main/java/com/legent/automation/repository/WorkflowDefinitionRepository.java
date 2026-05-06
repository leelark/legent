package com.legent.automation.repository;

import java.util.Optional;

import com.legent.automation.domain.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, WorkflowDefinition.WorkflowDefinitionId> {
    Optional<WorkflowDefinition> findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId(String workflowId, Integer version, String tenantId, String workspaceId);
    Optional<WorkflowDefinition> findTopByTenantIdAndWorkspaceIdAndWorkflowIdOrderByVersionDesc(String tenantId, String workspaceId, String workflowId);
    java.util.List<WorkflowDefinition> findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByVersionDesc(String tenantId, String workspaceId, String workflowId);
}
