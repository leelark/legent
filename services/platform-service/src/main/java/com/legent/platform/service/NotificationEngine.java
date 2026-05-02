package com.legent.platform.service;

import java.util.List;

import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.UnauthorizedException;
import com.legent.platform.domain.Notification;
import com.legent.platform.repository.NotificationRepository;
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
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setSeverity(severity != null ? severity : "INFO");
        notification.setLinkUrl(linkUrl);
        
        notificationRepository.save(notification);
        log.debug("Notification created for tenant {} user {} - {}", tenantId, userId, title);
        
        // MVP Note: For real-time updates, we would also push this over a WebSocket broker (e.g. STOMP over SockJS) to connected browsers.
    }

    /**
     * Gets unread notifications for a specific user within a tenant.
     * SECURITY: Filters by both tenantId and userId to prevent cross-user data access.
     */
    public List<Notification> getUnreadNotifications(String tenantId, String userId) {
        return notificationRepository.findByTenantIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(tenantId, userId);
    }

    /**
     * Marks a notification as read with full ownership validation.
     * SECURITY: Validates both tenant ownership and user ownership.
     */
    @Transactional
    public void markAsRead(String notificationId, String tenantId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));

        // Verify tenant ownership - prevent cross-tenant data manipulation
        if (!tenantId.equals(notification.getTenantId())) {
            log.warn("Cross-tenant access attempt: notification {} tenant {} accessed by tenant {}", 
                    notificationId, notification.getTenantId(), tenantId);
            throw new UnauthorizedException("Access denied to notification");
        }

        // Verify user ownership - prevent cross-user data manipulation
        if (!userId.equals(notification.getUserId())) {
            log.warn("Cross-user access attempt: notification {} user {} accessed by user {}",
                    notificationId, notification.getUserId(), userId);
            throw new UnauthorizedException("Access denied to notification");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
        log.debug("Notification {} marked as read for tenant {} user {}", notificationId, tenantId, userId);
    }
}
