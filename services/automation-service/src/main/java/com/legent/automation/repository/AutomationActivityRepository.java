package com.legent.automation.repository;

import com.legent.automation.domain.AutomationActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutomationActivityRepository extends JpaRepository<AutomationActivity, String> {
    List<AutomationActivity> findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, String workspaceId);
    Optional<AutomationActivity> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);
    boolean existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(String tenantId, String workspaceId, String name);
}
