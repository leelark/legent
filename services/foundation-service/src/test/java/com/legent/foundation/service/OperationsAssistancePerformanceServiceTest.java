package com.legent.foundation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.OperationsAssistRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.foundation.service.performance.OperationsAssistanceService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationsAssistancePerformanceServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private OperationsAssistanceService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new OperationsAssistanceService(repository, objectMapper);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void assist_mapsIncidentTelemetryToP1Actions() {
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OperationsAssistRequest request = new OperationsAssistRequest();
        request.setOperationType("INCIDENT");
        request.setTelemetry(Map.of(
                "successRatePercent", 94.0,
                "saturationPercent", 96.0,
                "p95LatencyMs", 2500,
                "errors", 1200
        ));

        Map<String, Object> result = service.assist(request);

        assertThat(result.get("severity")).isEqualTo("P1");
        assertThat(result.get("approvalRequired")).isEqualTo(true);
        assertThat((List<?>) result.get("recommendedActions")).anySatisfy(action ->
                assertThat(((Map<?, ?>) action).get("key")).isEqualTo("incident.route"));
    }

    @Test
    void assist_minimizesPersistedTelemetryToOperationalAllowList() throws Exception {
        when(repository.insert(eq("operations_assistance_reviews"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        OperationsAssistRequest request = new OperationsAssistRequest();
        request.setOperationType("LAUNCH");
        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("riskScore", 35);
        telemetry.put("successRatePercent", 99.5);
        telemetry.put("saturationPercent", 80);
        telemetry.put("p95LatencyMs", 1200);
        telemetry.put("errors", 0);
        telemetry.put("blockers", 0);
        telemetry.put("debugContext", "raw operational notes should not be persisted");
        telemetry.put("nested", Map.of("safe", "but not approved"));
        request.setTelemetry(telemetry);

        Map<String, Object> result = service.assist(request);

        ArgumentCaptor<Map<String, Object>> values = ArgumentCaptor.forClass(Map.class);
        verify(repository).insert(eq("operations_assistance_reviews"), values.capture(), anyList());
        Map<String, Object> persistedTelemetry = objectMapper.readValue(
                String.valueOf(values.getValue().get("telemetry")),
                new TypeReference<>() {}
        );

        assertThat(result.get("riskScore")).isEqualTo(55);
        assertThat(persistedTelemetry)
                .containsKeys("riskScore", "successRatePercent", "saturationPercent", "p95LatencyMs", "errors", "blockers")
                .doesNotContainKeys("debugContext", "nested");
        assertThat(values.getValue().toString()).doesNotContain("raw operational notes", "not approved");
    }

    @Test
    void assist_rejectsSensitiveTelemetryKeysBeforeRepositoryAccess() {
        OperationsAssistRequest request = new OperationsAssistRequest();
        request.setOperationType("INCIDENT");
        request.setTelemetry(Map.of(
                "successRatePercent", 99.0,
                "metadata", Map.of("apiToken", "secret-value")
        ));

        assertThatThrownBy(() -> service.assist(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("telemetry.metadata.apiToken");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void assist_requiresWorkspaceBeforeRepositoryAccess() {
        TenantContext.setWorkspaceId(null);
        OperationsAssistRequest request = new OperationsAssistRequest();
        request.setOperationType("BUILD");
        request.setTelemetry(Map.of("riskScore", 5));

        assertThatThrownBy(() -> service.assist(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId is required");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void assist_rejectsWorkspaceMismatchBeforeRepositoryAccess() {
        OperationsAssistRequest request = new OperationsAssistRequest();
        request.setWorkspaceId("workspace-2");
        request.setOperationType("BUILD");
        request.setTelemetry(Map.of("riskScore", 5));

        assertThatThrownBy(() -> service.assist(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId does not match");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }
}
