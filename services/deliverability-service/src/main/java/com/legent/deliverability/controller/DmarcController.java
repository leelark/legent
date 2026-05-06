package com.legent.deliverability.controller;

import com.legent.deliverability.domain.DmarcReport;
import com.legent.deliverability.repository.DmarcReportRepository;
import com.legent.deliverability.service.DmarcReportService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dmarc")
@RequiredArgsConstructor
public class DmarcController {
    private final DmarcReportRepository repo;
    private final DmarcReportService service;

    @GetMapping("/reports")
    public List<DmarcReport> getReports(@RequestParam String domain) {
        return repo.findByTenantIdAndWorkspaceIdAndDomain(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                domain
        );
    }

    @PostMapping("/ingest")
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
}
