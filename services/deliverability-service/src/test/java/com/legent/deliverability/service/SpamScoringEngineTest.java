package com.legent.deliverability.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpamScoringEngineTest {

    private final SpamScoringEngine engine = new SpamScoringEngine();

    @Test
    void calculateSpamScore_HealthyEmail_ReturnsZero() {
        int score = engine.calculateSpamScore("Welcome to Legent",
                "<html><body>We are glad you are here. <a href=\"https://example.com/unsubscribe\">unsubscribe</a></body></html>");
        assertEquals(0, score);
    }

    @Test
    void calculateSpamScore_SpammyKeywords_ReturnsHighRisk() {
        int score = engine.calculateSpamScore("URGENT: YOU ARE A WINNER", "Click here to claim your cash bonus and free money now!");
        // Subject URGENT (15) + WINNER (15) + All Caps (30) = 60
        // Body cash bonus (5) + free money (5) = 10 -> Total 70
        assertTrue(score > 60);
    }

    @Test
    void calculateSpamScore_CapsSubject_AddsPenalty() {
        int score = engine.calculateSpamScore("WELCOME TO LEGENT",
                "<html><body>We are glad you are here. <a href=\"https://example.com/unsubscribe\">unsubscribe</a></body></html>");
        assertEquals(34, score);
    }
}
