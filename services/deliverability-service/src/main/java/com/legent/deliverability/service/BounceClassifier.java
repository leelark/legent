package com.legent.deliverability.service;

import org.springframework.stereotype.Service;

@Service
public class BounceClassifier {
    public String classify(String reason) {
        if (reason == null) return "SOFT";
        String r = reason.toLowerCase();
        if (r.contains("mailbox full") || r.contains("quota")) return "SOFT";
        if (r.contains("user unknown") || r.contains("no such user") || r.contains("invalid recipient")) return "HARD";
        if (r.contains("blocked") || r.contains("blacklist")) return "BLOCK";
        return "SOFT";
    }
}
