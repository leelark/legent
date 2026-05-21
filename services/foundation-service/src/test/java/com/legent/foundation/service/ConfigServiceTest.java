package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.domain.ConfigVersionHistory;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
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

        private ConfigService configService;

        private static final String TENANT_ID = "test-tenant-001";

        @BeforeEach
        void setUp() {
                configService = new ConfigService(configRepository, configVersionHistoryRepository, configMapper,
                                cacheService, eventPublisher, new ObjectMapper());
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
                when(configVersionHistoryRepository.findMaxVersionByExactScopeAndConfigKey(TENANT_ID, null, null, "new.key"))
                                .thenReturn(0);

                ConfigDto.Response result = configService.createConfig(TENANT_ID, request);

                assertThat(result.getConfigKey()).isEqualTo("new.key");
                verify(eventPublisher).publish(anyString(), any());
                verify(cacheService, atLeastOnce()).deleteByPattern(anyString());
        }

        @Test
        @DisplayName("createConfig fails before side effects when scoped history cannot be recorded")
        void createConfig_failsWhenHistoryCannotBeRecorded() {
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("new.key")
                                .configValue("new.value")
                                .category("SYSTEM")
                                .build();
                SystemConfig entity = new SystemConfig();
                entity.setConfigKey("new.key");
                entity.setConfigValue("new.value");

                when(configRepository.existsByScope(TENANT_ID, null, null, "new.key"))
                                .thenReturn(false);
                when(configMapper.toEntity(request)).thenReturn(entity);
                when(configRepository.save(entity)).thenReturn(entity);
                when(configVersionHistoryRepository.findMaxVersionByExactScopeAndConfigKey(
                                TENANT_ID, null, null, "new.key"))
                                .thenThrow(new IllegalStateException("duplicate scoped version"));

                assertThatThrownBy(() -> configService.createConfig(TENANT_ID, request))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("duplicate scoped version");

                verify(eventPublisher, never()).publish(anyString(), any());
                verify(cacheService, never()).deleteByPattern(anyString());
                verify(configMapper, never()).toResponse(any(SystemConfig.class));
        }

        @Test
        @DisplayName("createConfig records tenant-scope history with exact scope")
        void createConfig_recordsTenantScopeVersion() {
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("scoped.key")
                                .configValue("tenant-value")
                                .category("SYSTEM")
                                .build();
                SystemConfig entity = new SystemConfig();
                entity.setConfigKey("scoped.key");
                when(configRepository.existsByScope(TENANT_ID, null, null, "scoped.key")).thenReturn(false);
                when(configMapper.toEntity(request)).thenReturn(entity);
                when(configRepository.save(entity)).then(returnsFirstArg());
                when(configMapper.toResponse(any(SystemConfig.class))).thenReturn(ConfigDto.Response.builder().build());
                when(configVersionHistoryRepository.findMaxVersionByExactScopeAndConfigKey(TENANT_ID, null, null, "scoped.key"))
                                .thenReturn(4);

                configService.createConfig(TENANT_ID, request);

                verify(configVersionHistoryRepository)
                                .findMaxVersionByExactScopeAndConfigKey(TENANT_ID, null, null, "scoped.key");
                ConfigVersionHistory history = savedHistory();
                assertThat(history.getVersion()).isEqualTo(5);
                assertThat(history.getTenantId()).isEqualTo(TENANT_ID);
                assertThat(history.getWorkspaceId()).isNull();
                assertThat(history.getEnvironmentId()).isNull();
                assertThat(entity.getConfigVersion()).isEqualTo(5);
        }

        @Test
        @DisplayName("upsertConfig records workspace-scope history independently from tenant scope")
        void upsertConfig_recordsWorkspaceScopeVersion() {
                TenantContext.setWorkspaceId("workspace-1");
                ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                                .configKey("scoped.key")
                                .configValue("workspace-value")
                                .scopeType(ConfigService.SCOPE_WORKSPACE)
                                .workspaceId("workspace-1")
                                .category("SYSTEM")
                                .build();
                when(configRepository.findByScope(TENANT_ID, "workspace-1", null, "scoped.key"))
                                .thenReturn(Optional.empty());
                when(configRepository.save(any(SystemConfig.class))).then(returnsFirstArg());
                when(configMapper.toResponse(any(SystemConfig.class))).thenReturn(ConfigDto.Response.builder().build());
                when(configVersionHistoryRepository.findMaxVersionByExactScopeAndConfigKey(
                                TENANT_ID, "workspace-1", null, "scoped.key")).thenReturn(null);

                configService.upsertConfig(TENANT_ID, "workspace-1", null, request);

                verify(configVersionHistoryRepository).findMaxVersionByExactScopeAndConfigKey(
                                TENANT_ID, "workspace-1", null, "scoped.key");
                ConfigVersionHistory history = savedHistory();
                assertThat(history.getVersion()).isEqualTo(1);
                assertThat(history.getTenantId()).isEqualTo(TENANT_ID);
                assertThat(history.getWorkspaceId()).isEqualTo("workspace-1");
                assertThat(history.getEnvironmentId()).isNull();
                assertThat(history.getChangeType()).isEqualTo(ConfigVersionHistory.ChangeType.CREATE.name());
        }

        @Test
        @DisplayName("updateConfig records environment-scope history from the existing config scope")
        void updateConfig_recordsEnvironmentScopeVersion() {
                TenantContext.setWorkspaceId("workspace-1");
                TenantContext.setEnvironmentId("prod");
                ConfigDto.UpdateRequest request = ConfigDto.UpdateRequest.builder()
                                .configValue("new-value")
                                .build();
                SystemConfig existing = environmentConfig("scoped.key", "old-value");
                when(configRepository.findByIdAndTenantIdAndDeletedAtIsNull("config-1", TENANT_ID))
                                .thenReturn(Optional.of(existing));
                when(configRepository.save(existing)).thenReturn(existing);
                when(configMapper.toResponse(existing)).thenReturn(ConfigDto.Response.builder().build());
                when(configVersionHistoryRepository.findMaxVersionByExactScopeAndConfigKey(
                                TENANT_ID, "workspace-1", "prod", "scoped.key")).thenReturn(2);

                configService.updateConfig("config-1", request);

                verify(configVersionHistoryRepository).findMaxVersionByExactScopeAndConfigKey(
                                TENANT_ID, "workspace-1", "prod", "scoped.key");
                ConfigVersionHistory history = savedHistory();
                assertThat(history.getVersion()).isEqualTo(3);
                assertThat(history.getTenantId()).isEqualTo(TENANT_ID);
                assertThat(history.getWorkspaceId()).isEqualTo("workspace-1");
                assertThat(history.getEnvironmentId()).isEqualTo("prod");
                assertThat(history.getChangeType()).isEqualTo(ConfigVersionHistory.ChangeType.UPDATE.name());
        }

        @Test
        @DisplayName("deleteConfig records environment-scope history without sharing tenant/key numbering")
        void deleteConfig_recordsEnvironmentScopeVersion() {
                TenantContext.setWorkspaceId("workspace-1");
                TenantContext.setEnvironmentId("prod");
                SystemConfig existing = environmentConfig("scoped.key", "old-value");
                when(configRepository.findByIdAndTenantIdAndDeletedAtIsNull("config-1", TENANT_ID))
                                .thenReturn(Optional.of(existing));
                when(configRepository.save(existing)).thenReturn(existing);
                when(configVersionHistoryRepository.findMaxVersionByExactScopeAndConfigKey(
                                TENANT_ID, "workspace-1", "prod", "scoped.key")).thenReturn(7);

                configService.deleteConfig("config-1");

                verify(configVersionHistoryRepository).findMaxVersionByExactScopeAndConfigKey(
                                TENANT_ID, "workspace-1", "prod", "scoped.key");
                ConfigVersionHistory history = savedHistory();
                assertThat(history.getVersion()).isEqualTo(8);
                assertThat(history.getTenantId()).isEqualTo(TENANT_ID);
                assertThat(history.getWorkspaceId()).isEqualTo("workspace-1");
                assertThat(history.getEnvironmentId()).isEqualTo("prod");
                assertThat(history.getChangeType()).isEqualTo(ConfigVersionHistory.ChangeType.DELETE.name());
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

        private ConfigVersionHistory savedHistory() {
                var captor = org.mockito.ArgumentCaptor.forClass(ConfigVersionHistory.class);
                verify(configVersionHistoryRepository).save(captor.capture());
                return captor.getValue();
        }

        private SystemConfig environmentConfig(String key, String value) {
                SystemConfig config = new SystemConfig();
                config.setTenantId(TENANT_ID);
                config.setWorkspaceId("workspace-1");
                config.setEnvironmentId("prod");
                config.setConfigKey(key);
                config.setConfigValue(value);
                config.setValueType(SystemConfig.ValueType.STRING);
                config.setCategory("SYSTEM");
                return config;
        }

}
