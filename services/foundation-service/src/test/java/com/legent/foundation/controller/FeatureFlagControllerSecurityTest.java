package com.legent.foundation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagControllerSecurityTest {

    @Test
    void getFlagByIdRequiresFeatureReadPermission() throws NoSuchMethodException {
        PreAuthorize preAuthorize = FeatureFlagController.class
                .getMethod("getFlag", String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("feature:read");
    }
}
