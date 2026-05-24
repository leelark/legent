package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.GlobalEnterpriseDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class EnterprisePackageServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private EnterprisePackageService service;

    @BeforeEach
    void setUp() {
        service = new EnterprisePackageService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void exportPackageBuildsCanonicalManifestFromAllowedWorkspaceObjects() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(environment("env-1", false)))
                .thenReturn(List.of(adminSetting("delivery.rate.window", "120")));

        Map<String, Object> result = service.exportPackage(exportRequest());

        assertThat(result)
                .containsEntry("status", "READY")
                .containsEntry("dryRunOnly", true)
                .containsEntry("objectCount", 1);
        Map<String, Object> manifest = (Map<String, Object>) result.get("manifest");
        assertThat(manifest)
                .containsEntry("schemaVersion", EnterprisePackageService.SCHEMA_VERSION)
                .containsKey("checksum");
        assertThat((Map<String, Object>) manifest.get("source"))
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("environmentId", "env-1");
        assertThat((List<Map<String, Object>>) manifest.get("objects"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item).containsEntry("type", "ADMIN_SETTING").containsEntry("key", "delivery.rate.window");
                    assertThat(item).containsKey("hash");
                });

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repository, org.mockito.Mockito.times(2)).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("scope_type IN ('WORKSPACE', 'ENVIRONMENT')")
                .contains("environment_id = :environmentId");
        assertThat(paramsCaptor.getAllValues().get(1))
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("environmentId", "env-1");
    }

    @Test
    void exportPackageFailsClosedWhenWorkspaceContextMissing() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> service.exportPackage(exportRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");
        verifyNoInteractions(repository);
    }

    @Test
    void exportPackageRejectsExplicitWorkspaceMismatchBeforeLookup() {
        GlobalEnterpriseDto.EnterprisePackageExportRequest request = exportRequest();
        request.setWorkspaceId("workspace-2");

        assertThatThrownBy(() -> service.exportPackage(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId does not match");
        verifyNoInteractions(repository);
    }

    @Test
    void exportPackageBlocksSensitiveValuesBeforeReturningManifest() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(environment("env-1", false)))
                .thenReturn(List.of(adminSetting("support.email", "admin@example.com")));

        assertThatThrownBy(() -> service.exportPackage(exportRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe package content");
    }

    @Test
    void validateImportReturnsDryRunCreateDiffForValidManifest() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(environment("env-1", false)))
                .thenReturn(List.of(adminSetting("delivery.rate.window", "120")))
                .thenReturn(List.of(environment("env-2", false)))
                .thenReturn(List.of());

        Map<String, Object> exported = service.exportPackage(exportRequest());
        GlobalEnterpriseDto.EnterprisePackageImportValidateRequest request = importRequest((Map<String, Object>) exported.get("manifest"));

        Map<String, Object> result = service.validateImport(request);

        assertThat(result)
                .containsEntry("status", "VALIDATED")
                .containsEntry("dryRunOnly", true)
                .containsEntry("liveApplySupported", false);
        assertThat((List<Map<String, Object>>) result.get("findings")).isEmpty();
        assertThat((List<Map<String, Object>>) result.get("diff"))
                .singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("type", "ADMIN_SETTING")
                        .containsEntry("key", "delivery.rate.window")
                        .containsEntry("action", "CREATE")
                        .containsEntry("liveMutation", false));
        assertThat((Map<String, Object>) result.get("diffSummary")).containsEntry("CREATE", 1L);
    }

    @Test
    void validateImportBlocksLockedTargetAndLiveApply() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(environment("env-1", false)))
                .thenReturn(List.of(adminSetting("delivery.rate.window", "120")))
                .thenReturn(List.of(environment("env-2", true)));

        Map<String, Object> exported = service.exportPackage(exportRequest());
        GlobalEnterpriseDto.EnterprisePackageImportValidateRequest request = importRequest((Map<String, Object>) exported.get("manifest"));
        request.setConfirmLiveApply(true);

        Map<String, Object> result = service.validateImport(request);

        assertThat(result).containsEntry("status", "BLOCKED");
        assertThat((List<Map<String, Object>>) result.get("diff")).isEmpty();
        assertThat(((List<Map<String, Object>>) result.get("findings")).stream().map(item -> String.valueOf(item.get("key"))).toList())
                .contains("target_environment.locked", "live_apply.unsupported", "idempotency_key.missing");
    }

    @Test
    void validateImportBlocksTamperedChecksumAndCrossWorkspaceSource() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(environment("env-1", false)))
                .thenReturn(List.of(adminSetting("delivery.rate.window", "120")))
                .thenReturn(List.of(environment("env-2", false)));

        Map<String, Object> exported = service.exportPackage(exportRequest());
        Map<String, Object> manifest = new LinkedHashMap<>((Map<String, Object>) exported.get("manifest"));
        manifest.put("name", "tampered");
        Map<String, Object> source = new LinkedHashMap<>((Map<String, Object>) manifest.get("source"));
        source.put("workspaceId", "workspace-2");
        manifest.put("source", source);

        Map<String, Object> result = service.validateImport(importRequest(manifest));

        assertThat(result).containsEntry("status", "BLOCKED");
        assertThat(((List<Map<String, Object>>) result.get("findings")).stream().map(item -> String.valueOf(item.get("key"))).toList())
                .contains("manifest.scope", "manifest.checksum");
    }

    private GlobalEnterpriseDto.EnterprisePackageExportRequest exportRequest() {
        GlobalEnterpriseDto.EnterprisePackageExportRequest request = new GlobalEnterpriseDto.EnterprisePackageExportRequest();
        request.setWorkspaceId("workspace-1");
        request.setSourceEnvironmentId("env-1");
        request.setPackageKey("pkg-core");
        request.setName("Core promotion package");
        request.setObjectTypes(List.of("ADMIN_SETTING"));
        request.setRequiredValidationGates(List.of("foundation-package-validate"));
        request.setMetadata(Map.of("purpose", "local-contract-test"));
        return request;
    }

    private GlobalEnterpriseDto.EnterprisePackageImportValidateRequest importRequest(Map<String, Object> manifest) {
        GlobalEnterpriseDto.EnterprisePackageImportValidateRequest request = new GlobalEnterpriseDto.EnterprisePackageImportValidateRequest();
        request.setWorkspaceId("workspace-1");
        request.setTargetEnvironmentId("env-2");
        request.setManifest(manifest);
        return request;
    }

    private Map<String, Object> environment(String id, boolean locked) {
        return map(
                "id", id,
                "tenant_id", "tenant-1",
                "workspace_id", "workspace-1",
                "environment_key", id.toUpperCase(),
                "is_locked", locked,
                "active_lock", locked
        );
    }

    private Map<String, Object> adminSetting(String key, String value) {
        return map(
                "config_key", key,
                "config_value", value,
                "module_key", "delivery",
                "category", "rate-control",
                "value_type", "INTEGER",
                "scope_type", "ENVIRONMENT",
                "environment_id", "env-1",
                "is_encrypted", false,
                "is_system", false,
                "config_version", 2,
                "dependency_keys", List.of("delivery.rate.enabled"),
                "validation_schema", Map.of("minimum", 1),
                "metadata", Map.of("owner", "platform"),
                "description", "Rate control window"
        );
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return values;
    }
}
