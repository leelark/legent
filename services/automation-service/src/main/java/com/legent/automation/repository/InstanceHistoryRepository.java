package com.legent.automation.repository;

import java.util.List;

import com.legent.automation.domain.InstanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface InstanceHistoryRepository extends JpaRepository<InstanceHistory, String> {
    List<InstanceHistory> findByTenantIdAndWorkspaceIdAndInstanceIdOrderByExecutedAtDesc(String tenantId, String workspaceId, String instanceId);
}
