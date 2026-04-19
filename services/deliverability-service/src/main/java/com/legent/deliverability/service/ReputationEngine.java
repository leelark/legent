package com.legent.deliverability.service;

import java.time.Instant;

import com.legent.deliverability.domain.DomainReputation;
import com.legent.deliverability.repository.DomainReputationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReputationEngine {

    private final DomainReputationRepository reputationRepository;

    @Transactional
    public void recordNegativeSignal(String tenantId, String domainId, String eventType) { // eventType = "HARD_BOUNCE", "COMPLAINT"
        // Normally computes against a time-series window.
        // For MVP, we will statically subtract points to demonstrate reputation decay.
        
        DomainReputation reputation = reputationRepository.findByDomainId(domainId)
                .orElseGet(() -> {
                    DomainReputation dr = new DomainReputation();
                    dr.setId(java.util.UUID.randomUUID().toString());
                    dr.setTenantId(tenantId);
                    dr.setDomainId(domainId);
                    return dr;
                });

        if ("HARD_BOUNCE".equals(eventType)) {
            BigDecimal newRate = reputation.getHardBounceRate().add(new BigDecimal("0.015"));
            reputation.setHardBounceRate(newRate);
            
            // Dynamic decay: Higher penalty if already struggling, but bounded by volume ratio
            double penalty = 2.0 * (1.0 + newRate.doubleValue());
            reputation.setReputationScore(Math.max(0, (int)(reputation.getReputationScore() - penalty)));
        } else if ("COMPLAINT".equals(eventType)) {
            BigDecimal newRate = reputation.getComplaintRate().add(new BigDecimal("0.025"));
            reputation.setComplaintRate(newRate);
            
            // Complaints exponentially decay
            double penalty = 5.0 * (1.0 + (newRate.doubleValue() * 2));
            reputation.setReputationScore(Math.max(0, (int)(reputation.getReputationScore() - penalty)));
        }

        reputation.setCalculatedAt(Instant.now());
        reputationRepository.save(reputation);
        
        log.info("Domain {} reputation updated to {}. Trigger: {}", domainId, reputation.getReputationScore(), eventType);
    }
}
