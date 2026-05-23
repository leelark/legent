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
    void validateProductionConfiguration_rejectsUnsafeDdlAutoValuesForProdProfiles() {
        for (String profile : new String[]{"prod", "production"}) {
            for (String ddlAuto : new String[]{"update", "create", "create-drop"}) {
                MockEnvironment environment = new MockEnvironment()
                        .withProperty("legent.security.jwt.secret", "real-secret-value-that-is-not-placeholder")
                        .withProperty("spring.jpa.hibernate.ddl-auto", ddlAuto);
                environment.setActiveProfiles(profile);

                RuntimeConfigurationGuard guard = new RuntimeConfigurationGuard(environment);

                assertThrows(IllegalStateException.class, guard::validateProductionConfiguration);
            }
        }
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

    @Test
    void validateProductionConfiguration_allowsLocalAndTestCreateDrop() {
        for (String profile : new String[]{"local", "test"}) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
            environment.setActiveProfiles(profile);

            RuntimeConfigurationGuard guard = new RuntimeConfigurationGuard(environment);

            assertDoesNotThrow(guard::validateProductionConfiguration);
        }
    }
}
