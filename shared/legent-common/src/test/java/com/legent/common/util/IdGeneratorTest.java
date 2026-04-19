package com.legent.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdGeneratorTest {

    @Test
    void newId_generatesUniqueUlid() {
        String id1 = IdGenerator.newId();
        String id2 = IdGenerator.newId();

        assertEquals(26, id1.length());
        assertEquals(26, id2.length());
        assertNotEquals(id1, id2);
    }

    @Test
    void prefixedIds_haveExpectedPrefixes() {
        assertTrue(IdGenerator.newIdempotencyKey().startsWith("IK-"));
        assertTrue(IdGenerator.newCorrelationId().startsWith("COR-"));
    }
}
