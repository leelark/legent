package com.legent.platform.repository;

import java.util.List;
import java.util.Optional;

import com.legent.platform.domain.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, String> {
    List<WebhookConfig> findByTenantIdAndWorkspaceIdAndIsActiveTrue(String tenantId, String workspaceId);
    List<WebhookConfig> findByTenantIdAndWorkspaceIdIsNullAndIsActiveTrue(String tenantId);
    Optional<WebhookConfig> findByIdAndTenantIdAndWorkspaceId(String id, String tenantId, String workspaceId);
    Optional<WebhookConfig> findByIdAndTenantIdAndWorkspaceIdIsNull(String id, String tenantId);
}
