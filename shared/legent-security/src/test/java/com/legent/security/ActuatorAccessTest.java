package com.legent.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActuatorAccessTest {

    @Test
    void publicEndpoints_exposeOnlyHealthAndPrometheus() {
        List<String> publicEndpoints = List.of(ActuatorAccess.PUBLIC_ENDPOINTS);

        assertTrue(publicEndpoints.contains("/actuator/health"));
        assertTrue(publicEndpoints.contains("/actuator/health/**"));
        assertTrue(publicEndpoints.contains("/actuator/prometheus"));
        assertEquals(1, ActuatorAccess.ADMIN_ENDPOINTS.length);
        assertEquals("/actuator/**", ActuatorAccess.ADMIN_ENDPOINTS[0]);
        assertEquals("PLATFORM_ADMIN", ActuatorAccess.ADMIN_ROLE);
    }
}
