package com.legent.deliverability.service;

import com.legent.deliverability.domain.DomainReputation;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.DomainReputationRepository;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PredictiveDeliverabilityService {

    private final SenderDomainRepository senderDomainRepository;
    private final DomainReputationRepository domainReputationRepository;
    private final SuppressionListRepository suppressionListRepository;
    private final DomainVerificationService domainVerificationService;

    @Transactional(readOnly = true)
    public Map<String, Object> predictRisk(String domainName, Integer plannedVolume, String isp) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        List<SenderDomain> domains = senderDomainRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId);
        SenderDomain domain = chooseDomain(domains, domainName);
        List<DomainReputation> reputations = domainReputationRepository.findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(tenantId, workspaceId);
        DomainReputation reputation = chooseReputation(reputations, domain);
        long complaints = suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "COMPLAINT");
        long hardBounces = suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "HARD_BOUNCE");

        int authRisk = authenticationRisk(domain);
        int reputationScore = reputation == null || reputation.getReputationScore() == null ? 75 : reputation.getReputationScore();
        double complaintRate = reputation == null ? 0 : decimal(reputation.getComplaintRate());
        double hardBounceRate = reputation == null ? 0 : decimal(reputation.getHardBounceRate());
        int volume = plannedVolume == null ? 0 : Math.max(0, plannedVolume);
        int blocklistRisk = blocklistRisk(reputationScore, complaintRate, hardBounceRate, complaints, hardBounces);
        int volumeRisk = volumeRisk(volume, reputationScore, domain);
        int ispModifier = ispModifier(isp);
        int riskScore = clamp((100 - reputationScore) + authRisk + blocklistRisk + volumeRisk + ispModifier, 0, 100);
        String riskBand = riskScore >= 70 ? "HIGH" : riskScore >= 40 ? "MEDIUM" : "LOW";
        int recommendedDailyCap = recommendedDailyCap(reputationScore, authRisk, blocklistRisk, volume);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("domain", domain == null ? domainName : domain.getDomainName());
        response.put("isp", isp == null || isp.isBlank() ? "blended" : isp.trim().toLowerCase(Locale.ROOT));
        response.put("riskScore", riskScore);
        response.put("riskBand", riskBand);
        response.put("reputationScore", reputationScore);
        response.put("authRisk", authRisk);
        response.put("blocklistRisk", blocklistRisk);
        response.put("volumeRisk", volumeRisk);
        response.put("complaintRate", complaintRate);
        response.put("hardBounceRate", hardBounceRate);
        response.put("recommendedDailyCap", recommendedDailyCap);
        response.put("warmupPhase", warmupPhase(reputationScore, volume, recommendedDailyCap));
        response.put("remediationGuidance", guidance(riskScore, authRisk, blocklistRisk, volumeRisk, recommendedDailyCap));
        response.put("ispReputation", ispReputation(reputationScore, ispModifier, riskScore));
        response.put("calculatedAt", Instant.now());
        return response;
    }

    private SenderDomain chooseDomain(List<SenderDomain> domains, String requested) {
        if (domains.isEmpty()) {
            return null;
        }
        if (requested == null || requested.isBlank()) {
            return domains.get(0);
        }
        return domains.stream()
                .filter(domain -> requested.equalsIgnoreCase(domain.getDomainName()))
                .findFirst()
                .orElse(domains.get(0));
    }

    private DomainReputation chooseReputation(List<DomainReputation> reputations, SenderDomain domain) {
        if (reputations.isEmpty()) {
            return null;
        }
        if (domain == null || domain.getId() == null) {
            return reputations.stream()
                    .max(Comparator.comparing(rep -> rep.getCalculatedAt() == null ? Instant.EPOCH : rep.getCalculatedAt()))
                    .orElse(null);
        }
        return reputations.stream()
                .filter(rep -> domain.getId().equals(rep.getDomainId()))
                .findFirst()
                .orElse(reputations.get(0));
    }

    private int authenticationRisk(SenderDomain domain) {
        if (domain == null) {
            return 45;
        }
        if (!domainVerificationService.hasFreshOwnershipProof(domain)) {
            return 70;
        }
        int risk = 0;
        if (!Boolean.TRUE.equals(domain.getSpfVerified())) {
            risk += 15;
        }
        if (!Boolean.TRUE.equals(domain.getDkimVerified())) {
            risk += 20;
        }
        if (!Boolean.TRUE.equals(domain.getDmarcVerified())) {
            risk += 20;
        }
        if (domain.getStatus() != SenderDomain.VerificationStatus.VERIFIED) {
            risk += 15;
        }
        return Math.min(70, risk);
    }

    private int blocklistRisk(int reputationScore, double complaintRate, double hardBounceRate, long complaints, long hardBounces) {
        int risk = 0;
        if (reputationScore < 60) {
            risk += 25;
        }
        if (complaintRate >= 0.003 || complaints >= 10) {
            risk += 25;
        }
        if (hardBounceRate >= 0.05 || hardBounces >= 50) {
            risk += 20;
        }
        return Math.min(60, risk);
    }

    private int volumeRisk(int plannedVolume, int reputationScore, SenderDomain domain) {
        if (plannedVolume <= 0) {
            return 0;
        }
        int cap = recommendedDailyCap(reputationScore, authenticationRisk(domain), 0, plannedVolume);
        if (plannedVolume > cap * 3) {
            return 30;
        }
        if (plannedVolume > cap) {
            return 15;
        }
        return 0;
    }

    private int recommendedDailyCap(int reputationScore, int authRisk, int blocklistRisk, int plannedVolume) {
        int base = reputationScore >= 90 ? 250_000 : reputationScore >= 75 ? 75_000 : reputationScore >= 60 ? 20_000 : 5_000;
        if (authRisk >= 40) {
            base = Math.min(base, 2_500);
        }
        if (blocklistRisk >= 40) {
            base = Math.min(base, 1_000);
        }
        return Math.max(250, plannedVolume <= 0 ? base : Math.min(base, Math.max(250, plannedVolume)));
    }

    private String warmupPhase(int reputationScore, int plannedVolume, int recommendedDailyCap) {
        if (reputationScore < 60 || plannedVolume > recommendedDailyCap) {
            return "REMEDIATE_AND_RAMP";
        }
        if (reputationScore < 80) {
            return "CONTROLLED_WARMUP";
        }
        return "NORMAL";
    }

    private List<String> guidance(int riskScore, int authRisk, int blocklistRisk, int volumeRisk, int cap) {
        List<String> guidance = new ArrayList<>();
        if (authRisk > 0) {
            guidance.add("Fix SPF, DKIM, DMARC, sender verification, and tracking-domain alignment before high-volume sends.");
        }
        if (blocklistRisk >= 25) {
            guidance.add("Pause risky sends, inspect complaint/bounce sources, suppress bad recipients, and request blocklist delisting after cleanup.");
        }
        if (volumeRisk > 0) {
            guidance.add("Throttle launch to " + cap + " recipients/day and prioritize recently engaged subscribers.");
        }
        if (riskScore >= 70) {
            guidance.add("Require deliverability approval before launch.");
        }
        if (guidance.isEmpty()) {
            guidance.add("Continue normal monitoring and keep ramp within provider capacity.");
        }
        return guidance;
    }

    private Map<String, Object> ispReputation(int reputationScore, int ispModifier, int riskScore) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gmail", clamp(reputationScore - Math.max(0, ispModifier), 0, 100));
        result.put("outlook", clamp(reputationScore - Math.max(0, riskScore - 60) / 2, 0, 100));
        result.put("yahoo", clamp(reputationScore - Math.max(0, riskScore - 50) / 2, 0, 100));
        result.put("corporate", clamp(reputationScore - Math.max(0, riskScore - 45), 0, 100));
        return result;
    }

    private int ispModifier(String isp) {
        if (isp == null || isp.isBlank()) {
            return 0;
        }
        return switch (isp.trim().toLowerCase(Locale.ROOT)) {
            case "gmail", "google" -> 8;
            case "outlook", "hotmail", "microsoft" -> 6;
            case "yahoo", "aol" -> 5;
            default -> 2;
        };
    }

    private double decimal(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
