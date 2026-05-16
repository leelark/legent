package com.legent.deliverability.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.deliverability.domain.DomainReputation;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.DomainReputationRepository;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.deliverability.service.DomainVerificationService;
import com.legent.deliverability.service.PredictiveDeliverabilityService;
import com.legent.deliverability.service.SpamScoringEngine;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final SpamScoringEngine spamScoringEngine;
    private final PredictiveDeliverabilityService predictiveDeliverabilityService;
    private final DomainVerificationService domainVerificationService;

    @GetMapping("/auth/checks")
    public ApiResponse<List<Map<String, Object>>> authChecks() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        List<SenderDomain> domains = senderDomainRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId);
        List<Map<String, Object>> result = domains.stream().map(domain -> {
            boolean freshProof = domainVerificationService.hasFreshOwnershipProof(domain);
            Map<String, Object> row = new HashMap<>();
            row.put("domainId", domain.getId());
            row.put("domain", domain.getDomainName());
            row.put("status", freshProof ? "VERIFIED" : "FAILED");
            row.put("spf", freshProof && Boolean.TRUE.equals(domain.getSpfVerified()));
            row.put("dkim", freshProof && Boolean.TRUE.equals(domain.getDkimVerified()));
            row.put("dmarc", freshProof && Boolean.TRUE.equals(domain.getDmarcVerified()));
            row.put("ownershipToken", freshProof);
            row.put("bimi", false);
            row.put("reverseDns", freshProof);
            row.put("trackingDomain", domain.getDomainName());
            row.put("lastVerifiedAt", domain.getLastVerifiedAt());
            row.put("ownershipTokenVerifiedAt", domain.getOwnershipTokenVerifiedAt());
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
    public ApiResponse<Map<String, Object>> inboxRisk(@RequestParam(required = false) String domain,
                                                      @RequestParam(required = false) String subject,
                                                      @RequestParam(required = false) String htmlBody) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();

        List<DomainReputation> reputations = domainReputationRepository
                .findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(tenantId, workspaceId);
        List<SenderDomain> domains = senderDomainRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId);
        double averageScore = reputations.isEmpty()
                ? 0
                : reputations.stream().mapToInt(rep -> rep.getReputationScore() != null ? rep.getReputationScore() : 0).average().orElse(0);
        long complaints = suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "COMPLAINT");
        long hardBounces = suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "HARD_BOUNCE");
        int contentRisk = spamScoringEngine.calculateSpamScore(subject, htmlBody);
        int authRisk = authenticationRisk(domains, domain);
        int linkRisk = linkRisk(htmlBody);

        int riskScore = Math.max(0, Math.min(100,
                (int) Math.round(((100 - averageScore) * 0.45) + (contentRisk * 0.25) + (authRisk * 0.20)
                        + (linkRisk * 0.10) + (complaints * 2) + hardBounces)));
        String riskBand = riskScore >= 70 ? "HIGH" : riskScore >= 40 ? "MEDIUM" : "LOW";

        Map<String, Object> response = new HashMap<>();
        response.put("riskScore", riskScore);
        response.put("riskBand", riskBand);
        response.put("authRisk", authRisk);
        response.put("contentRisk", contentRisk);
        response.put("linkRisk", linkRisk);
        response.put("avgReputation", Math.round(averageScore));
        response.put("complaints", complaints);
        response.put("hardBounces", hardBounces);
        response.put("breakdown", Map.of(
                "authentication", authRisk,
                "content", contentRisk,
                "links", linkRisk,
                "reputation", Math.max(0, 100 - Math.round(averageScore)),
                "complaints", complaints,
                "hardBounces", hardBounces
        ));
        response.put("recommendedActions", recommendedActions(riskScore, authRisk, contentRisk, linkRisk));
        response.put("calculatedAt", Instant.now());
        return ApiResponse.ok(response);
    }

    @GetMapping("/predictive-risk")
    public ApiResponse<Map<String, Object>> predictiveRisk(@RequestParam(required = false) String domain,
                                                           @RequestParam(required = false) Integer plannedVolume,
                                                           @RequestParam(required = false) String isp) {
        return ApiResponse.ok(predictiveDeliverabilityService.predictRisk(domain, plannedVolume, isp));
    }

    private int authenticationRisk(List<SenderDomain> domains, String requestedDomain) {
        if (domains.isEmpty()) {
            return 80;
        }
        SenderDomain selected = domains.stream()
                .filter(domain -> requestedDomain == null || requestedDomain.isBlank()
                        || domain.getDomainName().equalsIgnoreCase(requestedDomain))
                .findFirst()
                .orElse(domains.get(0));
        int risk = 0;
        if (!domainVerificationService.hasFreshOwnershipProof(selected)) {
            return 100;
        }
        if (!Boolean.TRUE.equals(selected.getSpfVerified())) {
            risk += 25;
        }
        if (!Boolean.TRUE.equals(selected.getDkimVerified())) {
            risk += 30;
        }
        if (!Boolean.TRUE.equals(selected.getDmarcVerified())) {
            risk += 30;
        }
        if (selected.getStatus() == null || !"VERIFIED".equalsIgnoreCase(selected.getStatus().name())) {
            risk += 15;
        }
        return Math.min(100, risk);
    }

    private int linkRisk(String htmlBody) {
        if (htmlBody == null || htmlBody.isBlank()) {
            return 25;
        }
        String lower = htmlBody.toLowerCase();
        int risk = 0;
        if (lower.contains("bit.ly") || lower.contains("tinyurl")) {
            risk += 40;
        }
        int links = lower.split("(?i)<a\\s+href=").length - 1;
        if (links > 25) {
            risk += 35;
        } else if (links > 10) {
            risk += 15;
        }
        return Math.min(100, risk);
    }

    private List<String> recommendedActions(int riskScore, int authRisk, int contentRisk, int linkRisk) {
        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        if (authRisk >= 40) {
            actions.add("Verify SPF, DKIM, DMARC, tracking domain, and sender domain before launch.");
        }
        if (contentRisk >= 40) {
            actions.add("Fix spam-like content, missing unsubscribe footer, high link density, or risky subject patterns.");
        }
        if (linkRisk >= 30) {
            actions.add("Use trusted branded links and reduce link density.");
        }
        if (riskScore >= 70) {
            actions.add("Block or defer launch until risk score falls below policy threshold.");
        } else if (riskScore >= 40) {
            actions.add("Throttle launch and send to engaged recipients first.");
        }
        return actions;
    }
}
