package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.ExtensionValidationRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.foundation.service.performance.ExtensionGovernanceService;
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
class ExtensionGovernancePerformanceServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private ExtensionGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new ExtensionGovernanceService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void validatePackage_failsUnsafeScriptsAndUnapprovedUnsafeScopes() {
        when(repository.queryForList(anyString(), any(Map.class))).thenReturn(List.of(Map.of(
                "id", "pkg-1",
                "workspace_id", "workspace-1",
                "approval_status", "PENDING",
                "scopes", List.of("tenant:*"),
                "manifest", Map.of("name", "Unsafe package", "version", "1.0.0", "entrypoint", "index.js"),
                "scripts", List.of(Map.of("name", "bad", "source", "const cp = require('child_process'); eval(input);")),
                "test_requirements", List.of("unit")
        )));
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        Map<String, Object> result = service.validatePackage("pkg-1", new ExtensionValidationRequest());

        assertThat(result.get("status")).isEqualTo("FAILED");
        assertThat(((List<?>) result.get("forbiddenTokens")).stream().map(String::valueOf).toList())
                .contains("child_process", "eval(");
        assertThat((List<?>) result.get("findings")).hasSizeGreaterThanOrEqualTo(2);
    }
}
