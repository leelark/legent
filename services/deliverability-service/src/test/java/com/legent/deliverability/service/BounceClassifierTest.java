package com.legent.deliverability.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BounceClassifierTest {
    @Test
    void classify_hardBounce() {
        var svc = new BounceClassifier();
        assertEquals("HARD", svc.classify("user unknown"));
    }
    @Test
    void classify_softBounce() {
        var svc = new BounceClassifier();
        assertEquals("SOFT", svc.classify("mailbox full"));
    }
    @Test
    void classify_blockBounce() {
        var svc = new BounceClassifier();
        assertEquals("BLOCK", svc.classify("blocked by policy"));
    }
}
