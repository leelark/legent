package com.legent.foundation.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.PagedResponse;
import com.legent.foundation.domain.AuditLog;
import com.legent.foundation.repository.AuditLogRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public PagedResponse<AuditLog> listLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String resourceType) {
        
        String tenantId = TenantContext.getTenantId();
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE));
        
        Page<AuditLog> result;
        if (resourceType != null) {
            result = auditLogRepository.findByTenantIdAndResourceType(tenantId, resourceType, pageRequest);
        } else {
            result = auditLogRepository.findByTenantId(tenantId, pageRequest);
        }

        return PagedResponse.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
