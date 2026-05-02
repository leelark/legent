package com.legent.platform.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.Notification;
import com.legent.platform.service.NotificationEngine;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/platform/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationEngine notificationEngine;

    @GetMapping
    public ApiResponse<List<Notification>> getUnreadNotifications(@RequestHeader("X-Tenant-Id") String tenantId) {
        // SECURITY: Get userId from authenticated security context, not from request
        String userId = getCurrentUserId();
        return ApiResponse.ok(notificationEngine.getUnreadNotifications(tenantId, userId));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable String id,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        // SECURITY: Get userId from authenticated security context
        String userId = getCurrentUserId();
        notificationEngine.markAsRead(id, tenantId, userId);
        return ApiResponse.ok(null);
    }

    /**
     * Extracts the current authenticated user ID from the security context.
     * This ensures notifications are scoped to the authenticated user only.
     */
    private String getCurrentUserId() {
        // First try from TenantContext if available
        String userId = TenantContext.getUserId();
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        
        // Fallback to SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        
        throw new IllegalStateException("User not authenticated");
    }
}
