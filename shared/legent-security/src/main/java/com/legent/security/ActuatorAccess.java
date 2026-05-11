package com.legent.security;

/**
 * Shared actuator route policy used by every service SecurityConfig.
 */
public final class ActuatorAccess {

    public static final String ADMIN_ROLE = "PLATFORM_ADMIN";

    public static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/prometheus"
    };

    public static final String[] ADMIN_ENDPOINTS = {
            "/actuator/**"
    };

    private ActuatorAccess() {
    }
}
