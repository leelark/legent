package com.legent.foundation.controller;

import com.legent.common.constant.AppConstants;

import java.util.Map;

import java.time.Instant;

import com.legent.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * Health and readiness endpoints.
 * These are tenant-free (no X-Tenant-Id required).
 */
@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/health")
@RequiredArgsConstructor
public class HealthController {

    private final ApplicationAvailability availability;

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "service", "foundation-service",
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/live")
    public ApiResponse<Map<String, String>> liveness() {
        LivenessState state = availability.getLivenessState();
        return ApiResponse.ok(Map.of(
                "status", state.name()
        ));
    }

    @GetMapping("/ready")
    public ApiResponse<Map<String, String>> readiness() {
        ReadinessState state = availability.getReadinessState();
        return ApiResponse.ok(Map.of(
                "status", state.name()
        ));
    }
}
