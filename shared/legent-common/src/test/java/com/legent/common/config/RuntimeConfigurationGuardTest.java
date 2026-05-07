package com.legent.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeConfigurationGuardTest {

    @Test
    void validateProductionConfiguration_rejectsPlaceholderSecret() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("legent.security.jwt.secret", "CHANGE_ME_REPLACE_IN_PRODUCTION");
        environment.setActiveProfiles("prod");

        RuntimeConfigurationGuard guard = new RuntimeConfigurationGuard(environment);

        assertThrows(IllegalStateException.class, guard::validateProductionConfiguration);
    }

    @Test
    void validateProductionConfiguration_rejectsMockRuntimeModes() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("legent.security.jwt.secret", "real-secret-value-that-is-not-placeholder")
                .withProperty("legent.deliverability.mock-dns", "true");
        environment.setActiveProfiles("prod");

        RuntimeConfigurationGuard guard = new RuntimeConfigurationGuard(environment);

        assertThrows(IllegalStateException.class, guard::validateProductionConfiguration);
    }

    @Test
    void validateProductionConfiguration_allowsLocalPlaceholders() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("legent.security.jwt.secret", "CHANGE_ME_REPLACE_IN_PRODUCTION")
                .withProperty("legent.deliverability.mock-dns", "true");
        environment.setActiveProfiles("local");

        RuntimeConfigurationGuard guard = new RuntimeConfigurationGuard(environment);

        assertDoesNotThrow(guard::validateProductionConfiguration);
    }
}
