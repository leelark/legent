package com.legent.deliverability.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.deliverability.domain.DomainReputation;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.DomainReputationRepository;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/deliverability")
@RequiredArgsConstructor
public class DeliverabilityInsightsController {

    private final SenderDomainRepository senderDomainRepository;
    private final DomainReputationRepository domainReputationRepository;
    private final SuppressionListRepository suppressionListRepository;

    @GetMapping("/auth/checks")
    public ApiResponse<List<Map<String, Object>>> authChecks() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        List<SenderDomain> domains = senderDomainRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId);
        List<Map<String, Object>> result = domains.stream().map(domain -> {
            Map<String, Object> row = new HashMap<>();
            row.put("domainId", domain.getId());
            row.put("domain", domain.getDomainName());
            row.put("status", domain.getStatus() != null ? domain.getStatus().name() : "PENDING");
            row.put("spf", domain.getSpfVerified());
            row.put("dkim", domain.getDkimVerified());
            row.put("dmarc", domain.getDmarcVerified());
            row.put("bimi", false);
            row.put("reverseDns", Boolean.TRUE.equals(domain.getSpfVerified()) && Boolean.TRUE.equals(domain.getDmarcVerified()));
            row.put("trackingDomain", domain.getDomainName());
            row.put("lastVerifiedAt", domain.getLastVerifiedAt());
            return row;
        }).toList();
        return ApiResponse.ok(result);
    }

    @GetMapping("/reputation/telemetry")
    public ApiResponse<List<Map<String, Object>>> reputationTelemetry() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        List<DomainReputation> reputations = domainReputationRepository
                .findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(tenantId, workspaceId);

        List<Map<String, Object>> result = reputations.stream().map(rep -> {
            Map<String, Object> row = new HashMap<>();
            row.put("domainId", rep.getDomainId());
            row.put("reputationScore", rep.getReputationScore());
            row.put("hardBounceRate", rep.getHardBounceRate());
            row.put("complaintRate", rep.getComplaintRate());
            row.put("calculatedAt", rep.getCalculatedAt());
            row.put("source", "DOMAIN_REPUTATION");
            return row;
        }).toList();
        return ApiResponse.ok(result);
    }

    @GetMapping("/inbox-risk")
    public ApiResponse<Map<String, Object>> inboxRisk() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();

        List<DomainReputation> reputations = domainReputationRepository
                .findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(tenantId, workspaceId);
        double averageScore = reputations.isEmpty()
                ? 0
                : reputations.stream().mapToInt(rep -> rep.getReputationScore() != null ? rep.getReputationScore() : 0).average().orElse(0);
        long complaints = suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "COMPLAINT");
        long hardBounces = suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "HARD_BOUNCE");

        int riskScore = Math.max(0, Math.min(100,
                (int) Math.round((100 - averageScore) + (complaints * 2) + hardBounces)));
        String riskBand = riskScore >= 70 ? "HIGH" : riskScore >= 40 ? "MEDIUM" : "LOW";

        Map<String, Object> response = new HashMap<>();
        response.put("riskScore", riskScore);
        response.put("riskBand", riskBand);
        response.put("avgReputation", Math.round(averageScore));
        response.put("complaints", complaints);
        response.put("hardBounces", hardBounces);
        response.put("calculatedAt", Instant.now());
        return ApiResponse.ok(response);
    }
}

