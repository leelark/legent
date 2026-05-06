package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.dto.BootstrapDto;
import com.legent.foundation.service.TenantBootstrapService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bootstrap")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')")
public class BootstrapController {

    private final TenantBootstrapService tenantBootstrapService;

    @GetMapping("/status")
    public ApiResponse<BootstrapDto.StatusResponse> status() {
        return ApiResponse.ok(tenantBootstrapService.getStatus(TenantContext.requireTenantId()));
    }

    @PostMapping("/repair")
    public ApiResponse<BootstrapDto.StatusResponse> repair(@RequestBody(required = false) BootstrapDto.RepairRequest request) {
        boolean force = request != null && request.isForce();
        return ApiResponse.ok(tenantBootstrapService.repair(TenantContext.requireTenantId(), force));
    }
}
