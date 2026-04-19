package com.legent.deliverability.service;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class SpamScoringEngine {

    private static final List<String> SPAM_KEYWORDS = List.of(
            "free money", "winner", "urgent", "viagra", "buy direct", 
            "cash bonus", "double your income", "earn $", "no catch"
    );

    /**
     * Analyzes email content and returns a score from 0 (Safe) to 100 (High Risk Spam).
     */
    public int calculateSpamScore(String subject, String htmlBody) {
        int score = 0;

        if (subject != null) {
            score += analyzeText(subject, 15); // Subject lines weigh heavily
            if (isAllUpperCase(subject)) {
                score += 30; // Shouting subject line is huge red flag
            }
        }

        if (htmlBody != null) {
            score += analyzeText(htmlBody, 5);
            score += analyzeHtmlHeuristics(htmlBody);
        }

        // Bayesian probability cap
        return Math.min((int) (score * 1.15), 100);
    }

    private int analyzeHtmlHeuristics(String htmlBody) {
        int penalty = 0;
        // High link density
        int linkCount = htmlBody.split("(?i)<a href=").length - 1;
        if (linkCount > 10) penalty += 5;
        if (linkCount > 25) penalty += 15;
        
        // Unencoded sketchy domains
        if (htmlBody.contains("bit.ly") || htmlBody.contains("tinyurl")) {
            penalty += 20;
        }
        return penalty;
    }

    private int analyzeText(String text, int weightPerMatch) {
        int localScore = 0;
        String lowerText = text.toLowerCase();
        for (String keyword : SPAM_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                localScore += weightPerMatch;
            }
        }
        return localScore;
    }

    private boolean isAllUpperCase(String text) {
        String clean = text.replaceAll("[^a-zA-Z]", "");
        return clean.length() > 3 && clean.equals(clean.toUpperCase());
    }
}
