package com.legent.foundation.controller;

import com.legent.foundation.dto.PublicContactDto;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class AdminContactRequestControllerSecurityTest {

    @Test
    void adminContactRequestsRequirePlatformAdminOnly() {
        PreAuthorize preAuthorize = AdminContactRequestController.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
        assertThat(preAuthorize.value()).doesNotContain("'ADMIN'", "'ORG_ADMIN'");
    }

    @Test
    void listDoesNotOverridePlatformAdminRestriction() throws NoSuchMethodException {
        PreAuthorize preAuthorize = AdminContactRequestController.class
                .getMethod("list", String.class, int.class, int.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNull();
    }

    @Test
    void updateStatusDoesNotOverridePlatformAdminRestriction() throws NoSuchMethodException {
        PreAuthorize preAuthorize = AdminContactRequestController.class
                .getMethod("updateStatus", String.class, PublicContactDto.StatusUpdateRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNull();
    }
}
