package com.legent.platform.repository;

import java.util.Optional;

import com.legent.platform.domain.WebhookConfig;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, String> {
    Slice<WebhookConfig> findByTenantIdAndWorkspaceIdAndIsActiveTrueOrderByIdAsc(
            String tenantId, String workspaceId, Pageable pageable);
    Slice<WebhookConfig> findByTenantIdAndWorkspaceIdIsNullAndIsActiveTrueOrderByIdAsc(
            String tenantId, Pageable pageable);
    Optional<WebhookConfig> findByIdAndTenantIdAndWorkspaceId(String id, String tenantId, String workspaceId);
    Optional<WebhookConfig> findByIdAndTenantIdAndWorkspaceIdIsNull(String id, String tenantId);
}
