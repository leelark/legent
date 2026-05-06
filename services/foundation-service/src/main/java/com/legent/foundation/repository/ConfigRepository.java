package com.legent.foundation.repository;

import java.util.Optional;

import java.util.List;

import com.legent.foundation.domain.SystemConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ConfigRepository extends JpaRepository<SystemConfig, String> {

    /**
     * Find config by key — tenant-specific first, then global fallback.
     */
    @Query(value = """
        SELECT c.*
        FROM system_configs c
        WHERE c.config_key = :key
          AND c.deleted_at IS NULL
          AND (
               (c.scope_type = 'ENVIRONMENT'
                    AND c.tenant_id = :tenantId
                    AND c.workspace_id = :workspaceId
                    AND c.environment_id = :environmentId)
               OR (c.scope_type = 'WORKSPACE'
                    AND c.tenant_id = :tenantId
                    AND c.workspace_id = :workspaceId
                    AND c.environment_id IS NULL)
               OR (c.scope_type = 'TENANT'
                    AND c.tenant_id = :tenantId
                    AND c.workspace_id IS NULL
                    AND c.environment_id IS NULL)
               OR (c.scope_type = 'GLOBAL'
                    AND c.tenant_id IS NULL
                    AND c.workspace_id IS NULL
                    AND c.environment_id IS NULL)
               OR (c.scope_type IS NULL AND (c.tenant_id = :tenantId OR c.tenant_id IS NULL))
          )
        ORDER BY CASE
            WHEN c.scope_type = 'ENVIRONMENT' THEN 4
            WHEN c.scope_type = 'WORKSPACE' THEN 3
            WHEN c.scope_type = 'TENANT' THEN 2
            WHEN c.scope_type = 'GLOBAL' THEN 1
            ELSE 0
        END DESC
    """, nativeQuery = true)
    List<SystemConfig> findByKeyWithFallback(
            @Param("key") String configKey,
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("environmentId") String environmentId);

    @Query("SELECT c FROM SystemConfig c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL")
    Page<SystemConfig> findByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT c FROM SystemConfig c WHERE c.tenantId IS NULL AND c.deletedAt IS NULL")
    Page<SystemConfig> findGlobalConfigs(Pageable pageable);

    @Query("SELECT c FROM SystemConfig c WHERE c.category = :category AND c.deletedAt IS NULL AND (c.tenantId = :tenantId OR c.tenantId IS NULL)")
    List<SystemConfig> findByCategory(@Param("category") String category, @Param("tenantId") String tenantId);

    Optional<SystemConfig> findByTenantIdAndConfigKeyAndDeletedAtIsNull(String tenantId, String configKey);

    @Query("SELECT c FROM SystemConfig c WHERE c.tenantId = :tenantId AND c.configKey = :configKey AND c.deletedAt IS NULL")
    Optional<SystemConfig> findByTenantIdAndConfigKey(@Param("tenantId") String tenantId, @Param("configKey") String configKey);

    boolean existsByTenantIdAndConfigKeyAndDeletedAtIsNull(String tenantId, String configKey);

    @Query("""
        SELECT c FROM SystemConfig c
        WHERE c.deletedAt IS NULL
          AND c.configKey = :configKey
          AND ((:tenantId IS NULL AND c.tenantId IS NULL) OR c.tenantId = :tenantId)
          AND ((:workspaceId IS NULL AND c.workspaceId IS NULL) OR c.workspaceId = :workspaceId)
          AND ((:environmentId IS NULL AND c.environmentId IS NULL) OR c.environmentId = :environmentId)
    """)
    Optional<SystemConfig> findByScope(@Param("tenantId") String tenantId,
                                       @Param("workspaceId") String workspaceId,
                                       @Param("environmentId") String environmentId,
                                       @Param("configKey") String configKey);

    @Query("""
        SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END
        FROM SystemConfig c
        WHERE c.deletedAt IS NULL
          AND c.configKey = :configKey
          AND ((:tenantId IS NULL AND c.tenantId IS NULL) OR c.tenantId = :tenantId)
          AND ((:workspaceId IS NULL AND c.workspaceId IS NULL) OR c.workspaceId = :workspaceId)
          AND ((:environmentId IS NULL AND c.environmentId IS NULL) OR c.environmentId = :environmentId)
    """)
    boolean existsByScope(@Param("tenantId") String tenantId,
                          @Param("workspaceId") String workspaceId,
                          @Param("environmentId") String environmentId,
                          @Param("configKey") String configKey);
}
