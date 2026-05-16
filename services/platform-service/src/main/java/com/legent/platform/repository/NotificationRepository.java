package com.legent.platform.repository;

import java.util.List;
import java.util.Optional;

import com.legent.platform.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    /**
     * Finds unread notifications for a specific user within a tenant workspace.
     * SECURITY: This ensures users can only see their own workspace notifications.
     */
    List<Notification> findByTenantIdAndWorkspaceIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(
            String tenantId, String workspaceId, String userId);

    Optional<Notification> findByIdAndTenantIdAndWorkspaceIdAndUserId(
            String id, String tenantId, String workspaceId, String userId);
}
