package com.legent.audience.repository;

import java.util.List;

import com.legent.audience.domain.DataExtensionGovernanceAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataExtensionGovernanceAuditRepository extends JpaRepository<DataExtensionGovernanceAudit, String> {

    List<DataExtensionGovernanceAudit> findTop20ByTenantIdAndWorkspaceIdAndDataExtensionIdOrderByCreatedAtDesc(
            String tenantId,
            String workspaceId,
            String dataExtensionId);
}
