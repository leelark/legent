package com.legent.tracking.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.security.TenantContext;
import com.legent.tracking.service.SegmentService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/segment")
@RequiredArgsConstructor
@Validated
public class SegmentController {
    private final SegmentService segmentService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getSegment(@RequestParam @NotBlank String field, @RequestParam @NotBlank String value) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(segmentService.getSegment(tenantId, workspaceId, field, value));
    }
}
