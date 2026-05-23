package com.legent.automation.repository;

import com.legent.automation.domain.Workflow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {
    List<Workflow> findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, String workspaceId, Pageable pageable);
    Optional<Workflow> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);
}
