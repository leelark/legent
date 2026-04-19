package com.legent.deliverability.service;

import com.legent.deliverability.domain.ReputationScore;
import com.legent.deliverability.repository.ReputationScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReputationService {
    private final ReputationScoreRepository repo;

    public ReputationScore updateReputation(String domain, double delta) {
        ReputationScore latest = repo.findTopByDomainOrderByLastUpdatedDesc(domain);
        double newScore = Math.max(0, Math.min(100, (latest != null ? latest.getScore() : 80) + delta));
        ReputationScore score = new ReputationScore();
        score.setDomain(domain);
        score.setScore(newScore);
        score.setLastUpdated(Instant.now());
        return repo.save(score);
    }
}
