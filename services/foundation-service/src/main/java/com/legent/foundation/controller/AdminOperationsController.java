package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.service.AdminOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/operations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')")
public class AdminOperationsController {

    private final AdminOperationsService adminOperationsService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(adminOperationsService.dashboard());
    }

    @GetMapping("/access")
    public ApiResponse<Map<String, Object>> accessOverview() {
        return ApiResponse.ok(adminOperationsService.accessOverview());
    }

    @GetMapping("/sync-events")
    public ApiResponse<List<Map<String, Object>>> syncEvents(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(adminOperationsService.syncEvents(status, limit));
    }
}
