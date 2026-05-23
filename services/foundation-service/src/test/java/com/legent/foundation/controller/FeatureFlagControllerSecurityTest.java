package com.legent.foundation.controller;

import com.legent.foundation.dto.FeatureFlagDto;
import com.legent.foundation.service.FeatureFlagService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagControllerSecurityTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getFlagByIdRequiresFeatureReadPermission() throws NoSuchMethodException {
        PreAuthorize preAuthorize = FeatureFlagController.class
                .getMethod("getFlag", String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("feature:read");
    }

    @Test
    void listFlagsRequiresTenantContextBeforeServiceLookup() {
        FeatureFlagController controller = new FeatureFlagController(featureFlagService);
        TenantContext.clear();

        assertThatThrownBy(() -> controller.listFlags(0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId is required");

        verifyNoInteractions(featureFlagService);
    }

    @Test
    void createFlagRequiresTenantContextBeforeServiceMutation() {
        FeatureFlagController controller = new FeatureFlagController(featureFlagService);
        FeatureFlagDto.CreateRequest request = FeatureFlagDto.CreateRequest.builder()
                .flagKey("beta_feature")
                .enabled(true)
                .build();
        TenantContext.clear();

        assertThatThrownBy(() -> controller.createFlag(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId is required");

        verifyNoInteractions(featureFlagService);
    }

    @Test
    void listFlagsUsesTrimmedTenantContextForTenantScopedAdminRead() {
        FeatureFlagController controller = new FeatureFlagController(featureFlagService);
        TenantContext.setTenantId(" tenant-1 ");
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(featureFlagService.listFlags(eq("tenant-1"), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        controller.listFlags(0, 20);

        verify(featureFlagService).listFlags(eq("tenant-1"), any(PageRequest.class));
    }
}
