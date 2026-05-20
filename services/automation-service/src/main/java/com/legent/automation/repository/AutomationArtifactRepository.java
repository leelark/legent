package com.legent.automation.repository;

import com.legent.automation.domain.AutomationArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AutomationArtifactRepository extends JpaRepository<AutomationArtifact, String> {
    Optional<AutomationArtifact> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
            String id, String tenantId, String workspaceId);
}
