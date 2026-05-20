package com.legent.foundation.service;

import java.util.List;
import java.util.Optional;

import com.legent.cache.service.CacheService;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.SystemConfig;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.mapper.ConfigMapper;
import com.legent.foundation.repository.ConfigRepository;
import com.legent.foundation.repository.ConfigVersionHistoryRepository;
import com.legent.kafka.producer.EventPublisher;
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
@DisplayName("ConfigService Unit Tests")
// 
class ConfigServiceTest {

        @Mock
        private ConfigRepository configRepository;
        @Mock
        private ConfigVersionHistoryRepository configVersionHistoryRepository;
        @Mock
        private ConfigMapper configMapper;
        @Mock
        private CacheService cacheService;
        @Mock
        private EventPublisher eventPublisher;

        @InjectMocks
        private ConfigService configService;

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
        @DisplayName("resolveConfig returns tenant-specific override when available")
        void resolveConfig_returnsTenanOverride() {
                SystemConfig tenantConfig = new SystemConfig();
                tenantConfig.setTenantId(TENANT_ID);
                tenantConfig.setConfigKey("smtp.provider");
                tenantConfig.setConfigValue("ses");

                ConfigDto.Response expected = ConfigDto.Response.builder()
                                .configKey("smtp.provider")
                                .configValue("ses")
                                .tenantId(TENANT_ID)
                                .build();

                when(cacheService.get(anyString(), eq(ConfigDto.Response.class)))
                                .thenReturn(Optional.empty());
                when(configRepository.findByKeyWithFallback("smtp.provider", TENANT_ID, null, null))
                                .thenReturn(List.of(tenantConfig));
                when(configMapper.toResponse(tenantConfig)).thenReturn(expected);

                ConfigDto.Response result = configService.resolveConfig("smtp.provider");

                assertThat(result.getConfigValue()).isEqualTo("ses");
                assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
                verify(cacheService).set(anyString(), eq(expected), any());
        }

        @Test
        @DisplayName("resolveConfig returns cached value when available")
        void resolveConfig_returnsCachedValue() {
                ConfigDto.Response cached = ConfigDto.Response.builder()
                                .configKey("smtp.provider")
                                .configValue("postal")
                                .build();

                when(cacheService.get(anyString(), eq(ConfigDto.Response.class)))
                                .thenReturn(Optional.of(cached));

                ConfigDto.Response result = configService.resolveConfig("smtp.provider");

                assertThat(result.getConfigValue()).isEqualTo("postal");
                verify(configRepository, never()).findByKeyWithFallback(anyString(), any(), any(), any());
        }

        @Test
        @DisplayName("resolveConfig throws NotFoundException when key not found")
        void resolveConfig_throwsNotFound() {
                when(cacheService.get(anyString(), eq(ConfigDto.Response.class)))
                                .thenReturn(Optional.empty());
                when(configRepository.findByKeyWithFallback("nonexistent", TENANT_ID, null, null))
                                .thenReturn(List.of());

                assertThatThrownBy(() -> configService.resolveConfig("nonexistent"))
                                .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("createConfig fails when key already exists")
        void createConfig_throwsConflict() {
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("existing.key")
                                .configValue("value")
                                .build();

                when(configRepository.existsByScope(TENANT_ID, null, null, "existing.key"))
                                .thenReturn(true);

                assertThatThrownBy(() -> configService.createConfig(TENANT_ID, request))
                                .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("createConfig saves and publishes event on success")
        void createConfig_success() {
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("new.key")
                                .configValue("new.value")
                                .category("SYSTEM")
                                .build();

                SystemConfig entity = new SystemConfig();
                entity.setConfigKey("new.key");
                entity.setConfigValue("new.value");

                ConfigDto.Response expected = ConfigDto.Response.builder()
                                .configKey("new.key")
                                .configValue("new.value")
                                .build();

                when(configRepository.existsByScope(TENANT_ID, null, null, "new.key"))
                                .thenReturn(false);
                when(configMapper.toEntity(request)).thenReturn(entity);
                when(configRepository.save(entity)).thenReturn(entity);
                when(configMapper.toResponse(entity)).thenReturn(expected);
                when(configVersionHistoryRepository.findMaxVersionByTenantIdAndConfigKey(TENANT_ID, "new.key"))
                                .thenReturn(0);

                ConfigDto.Response result = configService.createConfig(TENANT_ID, request);

                assertThat(result.getConfigKey()).isEqualTo("new.key");
                verify(eventPublisher).publish(anyString(), any());
                verify(cacheService, atLeastOnce()).deleteByPattern(anyString());
        }

        @Test
        @DisplayName("createConfig rejects workspace context mismatch before repository lookup")
        void createConfig_rejectsWorkspaceContextMismatch() {
                TenantContext.setWorkspaceId("workspace-1");
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("workspace.key")
                                .configValue("value")
                                .scopeType(ConfigService.SCOPE_WORKSPACE)
                                .workspaceId("workspace-2")
                                .build();

                assertThatThrownBy(() -> configService.createConfig(TENANT_ID, request))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("workspaceId");

                verify(configRepository, never()).existsByScope(any(), any(), any(), any());
                verify(configRepository, never()).save(any());
                verifyNoInteractions(configMapper);
        }

        @Test
        @DisplayName("createConfig rejects environment context mismatch before repository lookup")
        void createConfig_rejectsEnvironmentContextMismatch() {
                TenantContext.setWorkspaceId("workspace-1");
                TenantContext.setEnvironmentId("dev");
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("environment.key")
                                .configValue("value")
                                .scopeType(ConfigService.SCOPE_ENVIRONMENT)
                                .workspaceId("workspace-1")
                                .environmentId("prod")
                                .build();

                assertThatThrownBy(() -> configService.createConfig(TENANT_ID, request))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("environmentId");

                verify(configRepository, never()).existsByScope(any(), any(), any(), any());
                verify(configRepository, never()).save(any());
                verifyNoInteractions(configMapper);
        }

        @Test
        @DisplayName("upsertConfig rejects trusted workspace and body mismatch before repository lookup")
        void upsertConfig_rejectsTrustedWorkspaceBodyMismatch() {
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("workspace.key")
                                .configValue("value")
                                .scopeType(ConfigService.SCOPE_WORKSPACE)
                                .workspaceId("workspace-2")
                                .build();

                assertThatThrownBy(() -> configService.upsertConfig(TENANT_ID, "workspace-1", null, request))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("workspaceId");

                verify(configRepository, never()).findByScope(any(), any(), any(), any());
                verify(configRepository, never()).save(any());
                verifyNoInteractions(configMapper);
        }

        @Test
        @DisplayName("updateConfig resolves by current tenant before mutation")
        void updateConfig_rejectsConfigOutsideCurrentTenant() {
                ConfigDto.UpdateRequest request = ConfigDto.UpdateRequest.builder()
                                .configValue("new.value")
                                .build();

                when(configRepository.findByIdAndTenantIdAndDeletedAtIsNull("config-1", TENANT_ID))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> configService.updateConfig("config-1", request))
                                .isInstanceOf(NotFoundException.class);

                verify(configRepository, never()).findById(anyString());
                verify(configRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateConfig rejects workspace-scoped config outside current workspace")
        void updateConfig_rejectsWorkspaceScopedConfigOutsideCurrentWorkspace() {
                TenantContext.setWorkspaceId("workspace-1");
                ConfigDto.UpdateRequest request = ConfigDto.UpdateRequest.builder()
                                .configValue("new.value")
                                .build();
                SystemConfig existing = new SystemConfig();
                existing.setTenantId(TENANT_ID);
                existing.setWorkspaceId("workspace-2");
                existing.setConfigKey("workspace.key");
                existing.setConfigValue("old.value");

                when(configRepository.findByIdAndTenantIdAndDeletedAtIsNull("config-1", TENANT_ID))
                                .thenReturn(Optional.of(existing));

                assertThatThrownBy(() -> configService.updateConfig("config-1", request))
                                .isInstanceOf(NotFoundException.class);

                verify(configRepository, never()).save(any());
        }

        @Test
        @DisplayName("deleteConfig rejects environment-scoped config outside current environment")
        void deleteConfig_rejectsEnvironmentScopedConfigOutsideCurrentEnvironment() {
                TenantContext.setWorkspaceId("workspace-1");
                TenantContext.setEnvironmentId("dev");
                SystemConfig existing = new SystemConfig();
                existing.setTenantId(TENANT_ID);
                existing.setWorkspaceId("workspace-1");
                existing.setEnvironmentId("prod");
                existing.setConfigKey("environment.key");
                existing.setConfigValue("old.value");

                when(configRepository.findByIdAndTenantIdAndDeletedAtIsNull("config-1", TENANT_ID))
                                .thenReturn(Optional.of(existing));

                assertThatThrownBy(() -> configService.deleteConfig("config-1"))
                                .isInstanceOf(NotFoundException.class);

                verify(configRepository, never()).save(any());
        }
}
