package com.legent.deliverability.controller;

import com.legent.deliverability.domain.ReputationScore;
import com.legent.deliverability.repository.ReputationScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reputation")
@RequiredArgsConstructor
public class ReputationController {
    private final ReputationScoreRepository repo;

    @GetMapping("/{domain}")
    public ResponseEntity<Map<String, Object>> getScoreByDomain(@PathVariable String domain) {
        ReputationScore score = repo.findByDomain(domain);
        if (score == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "domain", score.getDomain(),
            "score", score.getScore(),
            "lastUpdated", score.getLastUpdated()
        ));
    }
}
