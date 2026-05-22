package com.legent.deliverability.controller;

import com.legent.deliverability.domain.ReputationScore;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.DomainReputationRepository;
import com.legent.deliverability.repository.ReputationScoreRepository;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reputation")
@RequiredArgsConstructor
public class ReputationController {
    private final DomainReputationRepository domainReputationRepository;
    private final SenderDomainRepository senderDomainRepository;
    private final ReputationScoreRepository repo;

    @GetMapping("/{domain}")
    @PreAuthorize("@rbacEvaluator.hasPermission('deliverability:read', principal.roles)")
    public ResponseEntity<Map<String, Object>> getScoreByDomain(@PathVariable String domain) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        String normalizedDomain = normalizeDomain(domain);

        SenderDomain senderDomain = senderDomainRepository
                .findByTenantIdAndWorkspaceIdAndDomainName(tenantId, workspaceId, normalizedDomain)
                .orElse(null);
        if (senderDomain != null) {
            return domainReputationRepository
                    .findByTenantIdAndWorkspaceIdAndDomainId(tenantId, workspaceId, senderDomain.getId())
                    .map(score -> {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("domain", senderDomain.getDomainName());
                        payload.put("score", score.getReputationScore());
                        payload.put("lastUpdated", score.getCalculatedAt());
                        payload.put("source", "DOMAIN_REPUTATION");
                        return ResponseEntity.ok(payload);
                    })
                    .orElseGet(() -> legacyScoreResponse(tenantId, workspaceId, normalizedDomain));
        }

        return legacyScoreResponse(tenantId, workspaceId, normalizedDomain);
    }

    private ResponseEntity<Map<String, Object>> legacyScoreResponse(String tenantId, String workspaceId, String domain) {
        ReputationScore score = repo.findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                tenantId,
                workspaceId,
                domain);
        if (score == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("domain", score.getDomain());
        payload.put("score", score.getScore());
        payload.put("lastUpdated", score.getLastUpdated());
        payload.put("source", "LEGACY_REPUTATION_SCORE");
        return ResponseEntity.ok(payload);
    }

    private String normalizeDomain(String domain) {
        return domain.trim().toLowerCase(Locale.ROOT);
    }
}
