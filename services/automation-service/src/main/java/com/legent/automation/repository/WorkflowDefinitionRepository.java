package com.legent.automation.repository;

import java.util.Optional;

import com.legent.automation.domain.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, WorkflowDefinition.WorkflowDefinitionId> {
    Optional<WorkflowDefinition> findByWorkflowIdAndVersionAndTenantId(String workflowId, Integer version, String tenantId);
}
