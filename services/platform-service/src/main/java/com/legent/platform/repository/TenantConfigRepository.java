package com.legent.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.legent.platform.domain.TenantConfig;

@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, String> {}
