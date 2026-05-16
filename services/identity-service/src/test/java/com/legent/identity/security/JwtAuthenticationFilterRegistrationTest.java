package com.legent.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthenticationFilterRegistrationTest {

    @Test
    void localJwtFilter_isNotComponentScanned() {
        assertNull(JwtAuthenticationFilter.class.getAnnotation(Component.class));
    }
}
