package com.legent.foundation.service;

import java.util.List;
import java.util.Optional;

import com.legent.cache.service.CacheService;
import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.FeatureFlag;
import com.legent.foundation.dto.FeatureFlagDto;
import com.legent.foundation.mapper.FeatureFlagMapper;
import com.legent.foundation.repository.FeatureFlagRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureFlagService Unit Tests")
// 
class FeatureFlagServiceTest {

        @Mock
        private FeatureFlagRepository featureFlagRepository;
        @Mock
        private FeatureFlagMapper featureFlagMapper;
        @Mock
        private CacheService cacheService;

        @InjectMocks
        private FeatureFlagService featureFlagService;

        private static final String TENANT_ID = "test-tenant-001";

        @BeforeEach
        void setUp() {
                TenantContext.setTenantId(TENANT_ID);
        }

        @AfterEach
        void tearDown() {
                TenantContext.clear();
        }

        @Test
        @DisplayName("evaluate returns tenant-specific flag when exists")
        void evaluate_tenantOverride() {
                FeatureFlag tenantFlag = new FeatureFlag();
                tenantFlag.setTenantId(TENANT_ID);
                tenantFlag.setFlagKey("dark_mode");
                tenantFlag.setEnabled(true);

                when(cacheService.get(anyString(), eq(FeatureFlagDto.EvaluationResult.class)))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.findByKeyWithFallback("dark_mode", TENANT_ID))
                                .thenReturn(List.of(tenantFlag));

                FeatureFlagDto.EvaluationResult result = featureFlagService.evaluate("dark_mode");

                assertThat(result.isEnabled()).isTrue();
                assertThat(result.getResolvedScope()).isEqualTo("TENANT");
        }

        @Test
        @DisplayName("evaluate returns global flag when no tenant override")
        void evaluate_globalDefault() {
                FeatureFlag globalFlag = new FeatureFlag();
                globalFlag.setTenantId(null);
                globalFlag.setFlagKey("beta_feature");
                globalFlag.setEnabled(false);

                when(cacheService.get(anyString(), eq(FeatureFlagDto.EvaluationResult.class)))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.findByKeyWithFallback("beta_feature", TENANT_ID))
                                .thenReturn(List.of(globalFlag));

                FeatureFlagDto.EvaluationResult result = featureFlagService.evaluate("beta_feature");

                assertThat(result.isEnabled()).isFalse();
                assertThat(result.getResolvedScope()).isEqualTo("GLOBAL");
        }

        @Test
        @DisplayName("evaluate returns disabled when flag not defined")
        void evaluate_undefinedFlag() {
                when(cacheService.get(anyString(), eq(FeatureFlagDto.EvaluationResult.class)))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.findByKeyWithFallback("unknown_flag", TENANT_ID))
                                .thenReturn(List.of());

                FeatureFlagDto.EvaluationResult result = featureFlagService.evaluate("unknown_flag");

                assertThat(result.isEnabled()).isFalse();
                assertThat(result.getResolvedScope()).isEqualTo("DEFAULT");
        }

        @Test
        @DisplayName("evaluate returns cached result when available")
        void evaluate_cachedResult() {
                FeatureFlagDto.EvaluationResult cached = FeatureFlagDto.EvaluationResult.builder()
                                .flagKey("cached_flag")
                                .enabled(true)
                                .resolvedScope("TENANT")
                                .build();

                when(cacheService.get(anyString(), eq(FeatureFlagDto.EvaluationResult.class)))
                                .thenReturn(Optional.of(cached));

                FeatureFlagDto.EvaluationResult result = featureFlagService.evaluate("cached_flag");

                assertThat(result.isEnabled()).isTrue();
                verify(featureFlagRepository, never()).findByKeyWithFallback(any(), any());
        }

        @Test
        @DisplayName("getFlag returns only tenant-scoped flags")
        void getFlag_returnsTenantScopedFlag() {
                FeatureFlag flag = tenantFlag("flag-1", TENANT_ID);
                FeatureFlagDto.Response response = responseFor(flag);
                when(featureFlagRepository.findByIdAndTenantIdAndDeletedAtIsNull("flag-1", TENANT_ID))
                                .thenReturn(Optional.of(flag));
                when(featureFlagMapper.toResponse(flag)).thenReturn(response);

                FeatureFlagDto.Response result = featureFlagService.getFlag("flag-1");

                assertThat(result).isSameAs(response);
                verify(featureFlagRepository, never()).findById(anyString());
        }

        @Test
        @DisplayName("getFlag hides flags from other tenants")
        void getFlag_hidesCrossTenantFlag() {
                when(featureFlagRepository.findByIdAndTenantIdAndDeletedAtIsNull("flag-1", TENANT_ID))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> featureFlagService.getFlag("flag-1"))
                                .isInstanceOf(NotFoundException.class);

                verify(featureFlagRepository, never()).findById(anyString());
                verify(featureFlagMapper, never()).toResponse(any());
        }

        @Test
        @DisplayName("getFlag fails closed when tenant context is missing")
        void getFlag_missingTenantFailsBeforeRepositoryLookup() {
                TenantContext.clear();

                assertThatThrownBy(() -> featureFlagService.getFlag("flag-1"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("tenantId is required");

                verifyNoInteractions(featureFlagRepository, featureFlagMapper);
        }

        @Test
        @DisplayName("updateFlag hides flags from other tenants")
        void updateFlag_hidesCrossTenantFlagWithoutSaving() {
                when(featureFlagRepository.findByIdAndTenantIdAndDeletedAtIsNull("flag-1", TENANT_ID))
                                .thenReturn(Optional.empty());

                FeatureFlagDto.UpdateRequest request = FeatureFlagDto.UpdateRequest.builder()
                                .enabled(true)
                                .build();

                assertThatThrownBy(() -> featureFlagService.updateFlag("flag-1", request))
                                .isInstanceOf(NotFoundException.class);

                verify(featureFlagRepository, never()).findById(anyString());
                verify(featureFlagRepository, never()).save(any());
                verifyNoInteractions(featureFlagMapper);
        }

        @Test
        @DisplayName("deleteFlag hides flags from other tenants")
        void deleteFlag_hidesCrossTenantFlagWithoutSaving() {
                when(featureFlagRepository.findByIdAndTenantIdAndDeletedAtIsNull("flag-1", TENANT_ID))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> featureFlagService.deleteFlag("flag-1"))
                                .isInstanceOf(NotFoundException.class);

                verify(featureFlagRepository, never()).findById(anyString());
                verify(featureFlagRepository, never()).save(any());
                verifyNoInteractions(featureFlagMapper);
        }

        private FeatureFlag tenantFlag(String id, String tenantId) {
                FeatureFlag flag = new FeatureFlag();
                flag.setId(id);
                flag.setTenantId(tenantId);
                flag.setFlagKey("beta_feature");
                flag.setEnabled(true);
                flag.setScope(FeatureFlag.FlagScope.TENANT);
                return flag;
        }

        private FeatureFlagDto.Response responseFor(FeatureFlag flag) {
                return FeatureFlagDto.Response.builder()
                                .id(flag.getId())
                                .tenantId(flag.getTenantId())
                                .flagKey(flag.getFlagKey())
                                .enabled(flag.isEnabled())
                                .scope(flag.getScope().name())
                                .build();
        }
}
