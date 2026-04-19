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
    @Query("""
        SELECT c FROM SystemConfig c
        WHERE c.configKey = :key
          AND (c.tenantId = :tenantId OR c.tenantId IS NULL)
          AND c.deletedAt IS NULL
        ORDER BY c.tenantId DESC NULLS LAST
    """)
    List<SystemConfig> findByKeyWithFallback(
            @Param("key") String configKey,
            @Param("tenantId") String tenantId);

    @Query("SELECT c FROM SystemConfig c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL")
    Page<SystemConfig> findByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT c FROM SystemConfig c WHERE c.tenantId IS NULL AND c.deletedAt IS NULL")
    Page<SystemConfig> findGlobalConfigs(Pageable pageable);

    @Query("SELECT c FROM SystemConfig c WHERE c.category = :category AND c.deletedAt IS NULL AND (c.tenantId = :tenantId OR c.tenantId IS NULL)")
    List<SystemConfig> findByCategory(@Param("category") String category, @Param("tenantId") String tenantId);

    Optional<SystemConfig> findByTenantIdAndConfigKeyAndDeletedAtIsNull(String tenantId, String configKey);

    boolean existsByTenantIdAndConfigKeyAndDeletedAtIsNull(String tenantId, String configKey);
}
