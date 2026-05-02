package com.legent.foundation.repository;

import java.util.List;
import java.util.Optional;

import com.legent.foundation.domain.ConfigVersionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for config version history operations.
 */
@Repository
public interface ConfigVersionHistoryRepository extends JpaRepository<ConfigVersionHistory, String> {

    Page<ConfigVersionHistory> findByTenantIdOrderByChangedAtDesc(String tenantId, Pageable pageable);

    List<ConfigVersionHistory> findByTenantIdAndConfigKeyOrderByVersionDesc(String tenantId, String configKey);

    @Query("SELECT v FROM ConfigVersionHistory v WHERE v.tenantId = :tenantId AND v.configKey = :configKey AND v.version = :version")
    Optional<ConfigVersionHistory> findByTenantIdAndConfigKeyAndVersion(String tenantId, String configKey, int version);

    @Query("SELECT MAX(v.version) FROM ConfigVersionHistory v WHERE v.tenantId = :tenantId AND v.configKey = :configKey")
    Integer findMaxVersionByTenantIdAndConfigKey(String tenantId, String configKey);

    Page<ConfigVersionHistory> findByTenantIdAndConfigKeyOrderByChangedAtDesc(String tenantId, String configKey, Pageable pageable);

    List<ConfigVersionHistory> findByTenantIdAndChangeTypeOrderByChangedAtDesc(String tenantId, String changeType);
}
