package com.legent.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityPropertiesTest {

    @Test
    void validateRejectsLiteralWildcardOriginWhenCredentialsAreEnabled() {
        SecurityProperties properties = validProperties();
        properties.getCors().setAllowedOrigins(List.of("https://app.legent.example", " * "));
        properties.getCors().setAllowCredentials(true);

        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateAllowsLiteralWildcardOriginWhenCredentialsAreDisabled() {
        SecurityProperties properties = validProperties();
        properties.getCors().setAllowedOrigins(List.of("*"));
        properties.getCors().setAllowCredentials(false);

        assertDoesNotThrow(properties::validate);
    }

    private SecurityProperties validProperties() {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.getCors().setAllowedOrigins(List.of("https://app.legent.example"));
        return properties;
    }
}
