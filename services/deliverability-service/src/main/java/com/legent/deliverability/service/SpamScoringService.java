package com.legent.deliverability.service;

import org.springframework.stereotype.Service;

@Service
public class SpamScoringService {
    public double score(String content) {
        // Simple spam scoring (replace with real rules)
        double score = 0;
        if (content == null) return score;
        String c = content.toLowerCase();
        if (c.contains("free") || c.contains("winner") || c.contains("prize")) score += 20;
        if (c.contains("unsubscribe")) score -= 5;
        if (c.contains("click here")) score += 10;
        return Math.max(0, Math.min(100, score));
    }
}
