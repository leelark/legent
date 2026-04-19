package com.legent.deliverability.controller;

import com.legent.deliverability.domain.ReputationScore;
import com.legent.deliverability.repository.ReputationScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reputation")
@RequiredArgsConstructor
public class ReputationController {
    private final ReputationScoreRepository repo;

    @GetMapping
    public List<ReputationScore> list(@RequestParam String domain) {
        return repo.findAll().stream().filter(r -> r.getDomain().equals(domain)).toList();
    }
}
