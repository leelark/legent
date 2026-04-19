package com.legent.common.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilTest {

    @Test
    void toJsonAndFromJson_roundTripWorks() {
        Payload payload = new Payload();
        payload.name = "legent";
        payload.count = 3;

        String json = JsonUtil.toJson(payload);
        Payload parsed = JsonUtil.fromJson(json, Payload.class);

        assertEquals("legent", parsed.name);
        assertEquals(3, parsed.count);
    }

    @Test
    void fromJsonSafe_onInvalidJson_returnsEmptyOptional() {
        Optional<Payload> parsed = JsonUtil.fromJsonSafe("{bad json}", Payload.class);

        assertTrue(parsed.isEmpty());
    }

    static class Payload {
        public String name;
        public int count;
    }
}
