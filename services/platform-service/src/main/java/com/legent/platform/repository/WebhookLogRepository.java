package com.legent.platform.repository;

import java.util.List;

import com.legent.platform.domain.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, String> {
    List<WebhookLog> findByTenantIdAndWorkspaceIdAndWebhookIdOrderByExecutedAtDesc(
            String tenantId, String workspaceId, String webhookId);
}
