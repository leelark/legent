package com.legent.deliverability.controller;

import com.legent.common.constant.AppConstants;
import com.legent.deliverability.domain.DmarcReport;
import com.legent.deliverability.repository.DmarcReportRepository;
import com.legent.deliverability.service.DmarcReportService;
import com.legent.security.TenantContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dmarc")
@RequiredArgsConstructor
public class DmarcController {
    private static final int DEFAULT_REPORT_LIMIT = AppConstants.DEFAULT_PAGE_SIZE;
    private static final int MAX_REPORT_LIMIT = AppConstants.MAX_PAGE_SIZE;

    private final DmarcReportRepository repo;
    private final DmarcReportService service;

    @GetMapping("/reports")
    @PreAuthorize("@rbacEvaluator.hasPermission('deliverability:read', principal.roles)")
    public List<DmarcReport> getReports(@RequestParam String domain, @RequestParam(required = false) Integer limit) {
        return repo.findByTenantIdAndWorkspaceIdAndDomain(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                domain,
                PageRequest.of(0, boundedReportLimit(limit))
        );
    }

    @PostMapping("/ingest")
    @PreAuthorize("@rbacEvaluator.hasPermission('deliverability:write', principal.roles)")
    public DmarcReport ingest(@RequestParam String domain, @RequestParam String type, @RequestBody String xml, @RequestParam(required = false) String summary) {
        return service.ingestReport(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                domain,
                xml,
                summary,
                type
        );
    }

    private static int boundedReportLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_REPORT_LIMIT;
        }
        return Math.min(limit, MAX_REPORT_LIMIT);
    }
}
