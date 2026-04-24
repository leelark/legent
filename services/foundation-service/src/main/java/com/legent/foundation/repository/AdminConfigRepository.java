package com.legent.foundation.repository;

import com.legent.foundation.domain.AdminConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminConfigRepository extends JpaRepository<AdminConfig, Long> {
    AdminConfig findByConfigKey(String configKey);
    List<AdminConfig> findByTenantIdOrTenantIdIsNull(String tenantId);
}
