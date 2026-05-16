package com.legent.platform.service;

import java.util.List;

import com.legent.common.exception.NotFoundException;
import com.legent.platform.domain.Notification;
import com.legent.platform.repository.NotificationRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationEngine {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void createNotification(String tenantId, String userId, String title, String message, String severity, String linkUrl) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setTenantId(tenantId);
        // Tenant-global platform events can intentionally omit workspace context; workspace APIs below do not expose them.
        notification.setWorkspaceId(TenantContext.getWorkspaceId());
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setSeverity(severity != null ? severity : "INFO");
        notification.setLinkUrl(linkUrl);
        
        notificationRepository.save(notification);
        log.debug("Notification created for tenant {} workspace {} user {} - {}",
                tenantId, notification.getWorkspaceId(), userId, title);
        
        // MVP Note: For real-time updates, we would also push this over a WebSocket broker (e.g. STOMP over SockJS) to connected browsers.
    }

    /**
     * Gets unread notifications for a specific user within a tenant workspace.
     * SECURITY: Filters by tenantId, workspaceId, and userId to prevent cross-scope data access.
     */
    public List<Notification> getUnreadNotifications(String tenantId, String workspaceId, String userId) {
        return notificationRepository.findByTenantIdAndWorkspaceIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(
                tenantId, workspaceId, userId);
    }

    /**
     * Marks a notification as read with full ownership validation.
     * SECURITY: Validates tenant, workspace, and user ownership.
     */
    @Transactional
    public void markAsRead(String notificationId, String tenantId, String workspaceId, String userId) {
        Notification notification = notificationRepository.findByIdAndTenantIdAndWorkspaceIdAndUserId(
                        notificationId, tenantId, workspaceId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));

        notification.setIsRead(true);
        notificationRepository.save(notification);
        log.debug("Notification {} marked as read for tenant {} workspace {} user {}",
                notificationId, tenantId, workspaceId, userId);
    }
}
