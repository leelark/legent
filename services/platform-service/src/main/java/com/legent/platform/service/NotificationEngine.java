package com.legent.platform.service;

import java.util.List;

import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.UnauthorizedException;
import com.legent.platform.domain.Notification;
import com.legent.platform.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor

public class NotificationEngine {

    private final NotificationRepository notificationRepository;

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
        log.debug("Notification created for tenant {} - {}", tenantId, title);
        
        // MVP Note: For real-time updates, we would also push this over a WebSocket broker (e.g. STOMP over SockJS) to connected browsers.
    }

    public List<Notification> getUnreadNotifications(String tenantId) {
        return notificationRepository.findByTenantIdAndIsReadFalseOrderByCreatedAtDesc(tenantId);
    }

    public void markAsRead(String notificationId, String tenantId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));

        // Verify ownership - prevent cross-tenant data manipulation
        if (!tenantId.equals(notification.getTenantId())) {
            throw new UnauthorizedException("Access denied to notification");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
        log.debug("Notification {} marked as read for tenant {}", notificationId, tenantId);
    }
}
