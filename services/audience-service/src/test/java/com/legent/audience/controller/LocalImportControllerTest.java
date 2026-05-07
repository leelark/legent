package com.legent.audience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalImportControllerTest {

    @Test
    void localImportController_isOnlyRegisteredForLocalAndTestProfiles() {
        Profile profile = LocalImportController.class.getAnnotation(Profile.class);

        assertNotNull(profile);
        assertEquals(Set.of("local", "test"), Set.of(profile.value()));
    }
}
