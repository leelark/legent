package com.legent.automation.repository;

import java.util.Collection;
import java.util.List;

import com.legent.automation.domain.InstanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface InstanceHistoryRepository extends JpaRepository<InstanceHistory, String> {
    List<InstanceHistory> findByTenantIdAndWorkspaceIdAndInstanceIdOrderByExecutedAtDesc(String tenantId, String workspaceId, String instanceId);

    @Query("""
            SELECT h
            FROM InstanceHistory h
            WHERE h.tenantId = :tenantId
              AND h.workspaceId = :workspaceId
              AND h.instanceId IN :instanceIds
            ORDER BY h.instanceId ASC, h.executedAt ASC, h.id ASC
            """)
    List<InstanceHistory> findScopedHistoryForInstances(@Param("tenantId") String tenantId,
                                                        @Param("workspaceId") String workspaceId,
                                                        @Param("instanceIds") Collection<String> instanceIds);
}
