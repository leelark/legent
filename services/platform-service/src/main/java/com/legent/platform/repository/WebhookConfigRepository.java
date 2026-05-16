package com.legent.platform.repository;

import java.util.List;
import java.util.Optional;

import com.legent.platform.domain.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, String> {
    List<WebhookConfig> findByTenantIdAndIsActiveTrue(String tenantId);
    Optional<WebhookConfig> findByIdAndTenantId(String id, String tenantId);
}
