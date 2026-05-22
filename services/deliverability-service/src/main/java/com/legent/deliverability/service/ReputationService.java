package com.legent.deliverability.service;

import com.legent.deliverability.domain.ReputationScore;
import com.legent.deliverability.repository.ReputationScoreRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReputationService {
    private final ReputationScoreRepository repo;

    public ReputationScore updateReputation(String domain, double delta) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        String normalizedDomain = domain.trim().toLowerCase(Locale.ROOT);
        ReputationScore latest = repo.findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                tenantId,
                workspaceId,
                normalizedDomain);
        double newScore = Math.max(0, Math.min(100, (latest != null ? latest.getScore() : 80) + delta));
        ReputationScore score = new ReputationScore();
        score.setTenantId(tenantId);
        score.setWorkspaceId(workspaceId);
        score.setDomain(normalizedDomain);
        score.setScore(newScore);
        score.setLastUpdated(Instant.now());
        return repo.save(score);
    }
}
