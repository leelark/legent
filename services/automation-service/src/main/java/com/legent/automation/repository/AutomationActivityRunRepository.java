package com.legent.automation.repository;

import com.legent.automation.domain.AutomationActivityRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationActivityRunRepository extends JpaRepository<AutomationActivityRun, String> {
    List<AutomationActivityRun> findByTenantIdAndWorkspaceIdAndActivityIdOrderByCreatedAtDesc(
            String tenantId, String workspaceId, String activityId);
}
