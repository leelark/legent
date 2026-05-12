package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.OperationsAssistRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.foundation.service.performance.OperationsAssistanceService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationsAssistancePerformanceServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private OperationsAssistanceService service;

    @BeforeEach
    void setUp() {
        service = new OperationsAssistanceService(repository, new ObjectMapper());
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
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

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
}
