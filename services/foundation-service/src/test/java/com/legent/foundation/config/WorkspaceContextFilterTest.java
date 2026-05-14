package com.legent.foundation.config;

import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceContextFilterTest {

    private final WorkspaceContextFilter filter = new WorkspaceContextFilter();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void performancePath_requiresWorkspaceHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/performance-intelligence/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("MISSING_WORKSPACE");
        assertThat(chainCalled.get()).isFalse();
    }

    @Test
    void workspaceQueryMismatch_returnsForbidden() throws Exception {
        TenantContext.setWorkspaceId("workspace-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/compliance/audit-evidence");
        request.addHeader(AppConstants.HEADER_WORKSPACE_ID, "workspace-1");
        request.setParameter("workspaceId", "workspace-2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("WORKSPACE_MISMATCH");
        assertThat(chainCalled.get()).isFalse();
    }
}
