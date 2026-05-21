package com.legent.foundation.repository;

import java.util.List;
import java.util.Optional;

import com.legent.foundation.domain.ConfigVersionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for config version history operations.
 */
@Repository
public interface ConfigVersionHistoryRepository extends JpaRepository<ConfigVersionHistory, String> {

    @Query("""
            SELECT v FROM ConfigVersionHistory v
            WHERE (v.tenantId = :tenantId OR (v.tenantId IS NULL AND :tenantId IS NULL))
              AND (v.workspaceId = :workspaceId OR (v.workspaceId IS NULL AND :workspaceId IS NULL))
              AND (v.environmentId = :environmentId OR (v.environmentId IS NULL AND :environmentId IS NULL))
            ORDER BY v.changedAt DESC
            """)
    Page<ConfigVersionHistory> findByExactScopeOrderByChangedAtDesc(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("environmentId") String environmentId,
            Pageable pageable);

    @Query("""
            SELECT v FROM ConfigVersionHistory v
            WHERE (v.tenantId = :tenantId OR (v.tenantId IS NULL AND :tenantId IS NULL))
              AND (v.workspaceId = :workspaceId OR (v.workspaceId IS NULL AND :workspaceId IS NULL))
              AND (v.environmentId = :environmentId OR (v.environmentId IS NULL AND :environmentId IS NULL))
              AND v.configKey = :configKey
            ORDER BY v.version DESC
            """)
    List<ConfigVersionHistory> findByExactScopeAndConfigKeyOrderByVersionDesc(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("environmentId") String environmentId,
            @Param("configKey") String configKey);

    @Query("""
            SELECT v FROM ConfigVersionHistory v
            WHERE (v.tenantId = :tenantId OR (v.tenantId IS NULL AND :tenantId IS NULL))
              AND (v.workspaceId = :workspaceId OR (v.workspaceId IS NULL AND :workspaceId IS NULL))
              AND (v.environmentId = :environmentId OR (v.environmentId IS NULL AND :environmentId IS NULL))
              AND v.configKey = :configKey
              AND v.version = :version
            """)
    Optional<ConfigVersionHistory> findByExactScopeAndConfigKeyAndVersion(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("environmentId") String environmentId,
            @Param("configKey") String configKey,
            @Param("version") int version);

    @Query("""
            SELECT MAX(v.version) FROM ConfigVersionHistory v
            WHERE (v.tenantId = :tenantId OR (v.tenantId IS NULL AND :tenantId IS NULL))
              AND (v.workspaceId = :workspaceId OR (v.workspaceId IS NULL AND :workspaceId IS NULL))
              AND (v.environmentId = :environmentId OR (v.environmentId IS NULL AND :environmentId IS NULL))
              AND v.configKey = :configKey
            """)
    Integer findMaxVersionByExactScopeAndConfigKey(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("environmentId") String environmentId,
            @Param("configKey") String configKey);
}
