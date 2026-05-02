package com.legent.platform.repository;

import java.util.List;

import com.legent.platform.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByTenantIdAndIsReadFalseOrderByCreatedAtDesc(String tenantId);
    
    /**
     * Finds unread notifications for a specific user within a tenant.
     * SECURITY: This ensures users can only see their own notifications.
     */
    List<Notification> findByTenantIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(String tenantId, String userId);
}
