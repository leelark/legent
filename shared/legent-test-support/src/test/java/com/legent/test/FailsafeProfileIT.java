package com.legent.test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FailsafeProfileIT {

    @Test
    void runsOnlyWhenIntegrationProfileEnablesFailsafe() {
        assertEquals("true", System.getProperty("legent.integration-tests.enabled"));
    }
}
