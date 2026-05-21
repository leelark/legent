package com.legent.foundation.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.ConfigVersionHistory;
import com.legent.foundation.domain.SystemConfig;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.repository.ConfigRepository;
import com.legent.foundation.repository.ConfigVersionHistoryRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigVersioningService scope tests")
class ConfigVersioningServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String CONFIG_KEY = "delivery.max-retries";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String ENVIRONMENT_ID = "dev";
    private static final String USER_ID = "user-1";

    @Mock
    private ConfigVersionHistoryRepository versionHistoryRepository;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ConfigService configService;

    @Mock
    private AuditService auditService;

    private ConfigVersioningService service;

    @BeforeEach
    void setUp() {
        service = new ConfigVersioningService(
                versionHistoryRepository,
                configRepository,
                configService,
                auditService
        );
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
        TenantContext.setEnvironmentId(ENVIRONMENT_ID);
        TenantContext.setUserId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getConfigVersionHistory_readsOnlyRequestedExactScopeAndKey() {
        ConfigVersionHistory v2 = history("value-2", 2);
        ConfigVersionHistory v1 = history("value-1", 1);
        when(versionHistoryRepository.findByExactScopeAndConfigKeyOrderByVersionDesc(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY))
                .thenReturn(List.of(v2, v1));

        List<ConfigVersionHistory> result = service.getConfigVersionHistory(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY);

        assertThat(result).containsExactly(v2, v1);
        verify(versionHistoryRepository).findByExactScopeAndConfigKeyOrderByVersionDesc(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY);
    }

    @Test
    void getTenantVersionHistory_readsOnlyRequestedExactScope() {
        Pageable pageable = PageRequest.of(0, 20);
        ConfigVersionHistory entry = history("tenant-value", 3);
        Page<ConfigVersionHistory> page = new PageImpl<>(List.of(entry), pageable, 1);
        when(versionHistoryRepository.findByExactScopeOrderByChangedAtDesc(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, pageable))
                .thenReturn(page);

        Page<ConfigVersionHistory> result = service.getTenantVersionHistory(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, pageable);

        assertThat(result.getContent()).containsExactly(entry);
        verify(versionHistoryRepository).findByExactScopeOrderByChangedAtDesc(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, pageable);
    }

    @Test
    void getConfigVersion_readsOnlyRequestedExactScopeKeyAndVersion() {
        ConfigVersionHistory target = history("value-2", 2);
        when(versionHistoryRepository.findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 2))
                .thenReturn(Optional.of(target));

        ConfigVersionHistory result = service.getConfigVersion(TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 2);

        assertThat(result).isSameAs(target);
        verify(versionHistoryRepository).findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 2);
    }

    @Test
    void getConfigVersion_failsClosedWhenVersionIsOutsideExactScope() {
        when(versionHistoryRepository.findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 7))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConfigVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 7))
                .isInstanceOf(NotFoundException.class);

        verify(versionHistoryRepository).findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 7);
    }

    @Test
    void compareVersions_usesExactScopeForBothVersions() {
        Instant changedAt1 = Instant.parse("2026-05-20T10:00:00Z");
        Instant changedAt2 = Instant.parse("2026-05-20T11:00:00Z");
        ConfigVersionHistory v1 = history("5", 1);
        v1.setChangedAt(changedAt1);
        v1.setChangedBy("user-a");
        ConfigVersionHistory v2 = history("10", 2);
        v2.setChangedAt(changedAt2);
        v2.setChangedBy("user-b");
        when(versionHistoryRepository.findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 1))
                .thenReturn(Optional.of(v1));
        when(versionHistoryRepository.findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 2))
                .thenReturn(Optional.of(v2));

        Map<String, Object> result = service.compareVersions(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 1, 2);

        assertThat(result)
                .containsEntry("tenantId", TENANT_ID)
                .containsEntry("workspaceId", WORKSPACE_ID)
                .containsEntry("environmentId", ENVIRONMENT_ID)
                .containsEntry("configKey", CONFIG_KEY)
                .containsEntry("version1", 1)
                .containsEntry("version2", 2)
                .containsEntry("value1", "5")
                .containsEntry("value2", "10")
                .containsEntry("areEqual", false)
                .containsEntry("changedAt1", changedAt1)
                .containsEntry("changedAt2", changedAt2)
                .containsEntry("changedBy1", "user-a")
                .containsEntry("changedBy2", "user-b");
    }

    @Test
    void rollbackConfig_restoresExactCurrentConfigAndRecordsRollbackVersion() {
        ConfigVersionHistory target = history("5", 2);
        SystemConfig current = currentConfig("10", 4, WORKSPACE_ID, ENVIRONMENT_ID);
        ConfigDto.Response response = ConfigDto.Response.builder()
                .configKey(CONFIG_KEY)
                .configValue("5")
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .environmentId(ENVIRONMENT_ID)
                .build();
        when(versionHistoryRepository.findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 2))
                .thenReturn(Optional.of(target));
        when(configRepository.findByScope(TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY))
                .thenReturn(Optional.of(current));
        when(versionHistoryRepository.findMaxVersionByExactScopeAndConfigKey(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY))
                .thenReturn(4);
        when(configRepository.save(current)).thenReturn(current);
        when(configService.finalizeRollback(current)).thenReturn(response);

        ConfigDto.Response result = service.rollbackConfig(TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 2);

        assertThat(result).isSameAs(response);
        assertThat(current.getConfigValue()).isEqualTo("5");
        assertThat(current.getConfigVersion()).isEqualTo(5);
        assertThat(current.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(current.getEnvironmentId()).isEqualTo(ENVIRONMENT_ID);
        assertThat(current.getLastModifiedBy()).isEqualTo(USER_ID);
        verify(configRepository).save(current);

        ArgumentCaptor<ConfigVersionHistory> rollbackCaptor = ArgumentCaptor.forClass(ConfigVersionHistory.class);
        verify(versionHistoryRepository).save(rollbackCaptor.capture());
        ConfigVersionHistory rollbackEntry = rollbackCaptor.getValue();
        assertThat(rollbackEntry.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(rollbackEntry.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(rollbackEntry.getEnvironmentId()).isEqualTo(ENVIRONMENT_ID);
        assertThat(rollbackEntry.getConfigKey()).isEqualTo(CONFIG_KEY);
        assertThat(rollbackEntry.getConfigValue()).isEqualTo("5");
        assertThat(rollbackEntry.getVersion()).isEqualTo(5);
        assertThat(rollbackEntry.getChangeType()).isEqualTo(ConfigVersionHistory.ChangeType.ROLLBACK.name());
        assertThat(rollbackEntry.getChangedBy()).isEqualTo(USER_ID);
        assertThat(rollbackEntry.getRollbackToVersion()).isEqualTo(2);
        ArgumentCaptor<Map<String, Object>> auditCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).log(eq("CONFIG_ROLLBACK"), eq("SystemConfig"), eq(CONFIG_KEY), auditCaptor.capture());
        assertThat(auditCaptor.getValue())
                .containsEntry("tenantId", TENANT_ID)
                .containsEntry("workspaceId", WORKSPACE_ID)
                .containsEntry("environmentId", ENVIRONMENT_ID)
                .containsEntry("fromVersion", 4)
                .containsEntry("toVersion", 2)
                .containsEntry("newVersion", 5);
        verify(configService).finalizeRollback(current);
    }

    @Test
    void compareVersions_handlesNullValuesWithoutThrowing() {
        ConfigVersionHistory v1 = history(null, 1);
        ConfigVersionHistory v2 = history(null, 2);
        when(versionHistoryRepository.findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 1))
                .thenReturn(Optional.of(v1));
        when(versionHistoryRepository.findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 2))
                .thenReturn(Optional.of(v2));

        Map<String, Object> result = service.compareVersions(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 1, 2);

        assertThat(result).containsEntry("areEqual", true);
    }

    @Test
    void rollbackConfig_failsClosedWhenTargetVersionIsOutsideExactScope() {
        when(versionHistoryRepository.findByExactScopeAndConfigKeyAndVersion(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 9))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rollbackConfig(
                TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY, 9))
                .isInstanceOf(NotFoundException.class);

        verify(configRepository, never()).findByScope(TENANT_ID, WORKSPACE_ID, ENVIRONMENT_ID, CONFIG_KEY);
        verify(configRepository, never()).save(any());
        verify(versionHistoryRepository, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any());
    }

    private ConfigVersionHistory history(String value, int version) {
        ConfigVersionHistory history = new ConfigVersionHistory();
        history.setTenantId(TENANT_ID);
        history.setWorkspaceId(WORKSPACE_ID);
        history.setEnvironmentId(ENVIRONMENT_ID);
        history.setConfigKey(CONFIG_KEY);
        history.setConfigValue(value);
        history.setValueType(SystemConfig.ValueType.INTEGER.name());
        history.setCategory("DELIVERY");
        history.setDescription("Delivery retry limit");
        history.setEncrypted(false);
        history.setVersion(version);
        history.setChangeType(ConfigVersionHistory.ChangeType.UPDATE.name());
        history.setChangedBy(USER_ID);
        history.setChangedAt(Instant.parse("2026-05-20T09:00:00Z"));
        return history;
    }

    private SystemConfig currentConfig(String value, int version, String workspaceId, String environmentId) {
        SystemConfig config = new SystemConfig();
        config.setTenantId(TENANT_ID);
        config.setConfigKey(CONFIG_KEY);
        config.setConfigValue(value);
        config.setValueType(SystemConfig.ValueType.INTEGER);
        config.setCategory("DELIVERY");
        config.setDescription("Delivery retry limit");
        config.setEncrypted(false);
        config.setConfigVersion(version);
        config.setWorkspaceId(workspaceId);
        config.setEnvironmentId(environmentId);
        config.setScopeType(SystemConfig.ScopeType.ENVIRONMENT);
        return config;
    }
}
