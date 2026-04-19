package com.legent.platform.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.Notification;
import com.legent.platform.service.NotificationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/platform/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationEngine notificationEngine;

    @GetMapping
    public ApiResponse<List<Notification>> getUnreadNotifications(@RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.ok(notificationEngine.getUnreadNotifications(tenantId));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable String id) {
        notificationEngine.markAsRead(id);
        return ApiResponse.ok(null);
    }
}
