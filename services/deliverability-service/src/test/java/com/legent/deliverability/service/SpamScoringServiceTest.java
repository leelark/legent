package com.legent.deliverability.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpamScoringServiceTest {
    @Test
    void score_spammyContent() {
        var svc = new SpamScoringService();
        assertTrue(svc.score("You are a winner! Click here for free prize") > 20);
    }
    @Test
    void score_hamContent() {
        var svc = new SpamScoringService();
        assertTrue(svc.score("Welcome to our newsletter. Unsubscribe below.") < 10);
    }
}
