package com.legent.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantFilterTest {

    private final TenantFilter tenantFilter = new TenantFilter();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_whenTenantMissingOnProtectedPath_returnsBadRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("X-Tenant-Id header is required"));
        assertTrue(!chainCalled.get());
    }

    @Test
    void doFilter_whenTenantHeaderPresent_setsAndClearsContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.addHeader("X-Tenant-Id", "tenant-1");
        request.addHeader("X-Workspace-Id", "workspace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean sawTenantInChain = new AtomicBoolean(false);
        AtomicBoolean sawWorkspaceInChain = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> {
            sawTenantInChain.set("tenant-1".equals(TenantContext.getTenantId()));
            sawWorkspaceInChain.set("workspace-1".equals(TenantContext.getWorkspaceId()));
        };

        tenantFilter.doFilter(request, response, chain);

        assertTrue(sawTenantInChain.get());
        assertTrue(sawWorkspaceInChain.get());
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void doFilter_whenTenantQueryParamOnProtectedPath_returnsBadRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.setParameter("t", "tenant-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(!chainCalled.get());
    }

    @Test
    void doFilter_whenTenantFreePrefixOnlyMatchesBoundary_returnsBadRequestForSiblingPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/authentication/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(!chainCalled.get());
    }

    @Test
    void doFilter_onTrackingPath_acceptsTenantQueryParameter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tracking/o.gif");
        request.setParameter("t", "tenant-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean sawTenantInChain = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> sawTenantInChain.set("tenant-1".equals(TenantContext.getTenantId()));

        tenantFilter.doFilter(request, response, chain);

        assertTrue(sawTenantInChain.get());
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void doFilter_whenHeaderConflictsWithAuthenticatedTenant_returnsForbiddenAndClearsContext() throws Exception {
        TenantContext.setTenantId("tenant-a");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.addHeader("X-Tenant-Id", "tenant-b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(!chainCalled.get());
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void doFilter_whenHeaderConflictsWithAuthenticatedWorkspace_returnsForbiddenAndClearsContext() throws Exception {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setWorkspaceId("workspace-a");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-Workspace-Id", "workspace-b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(!chainCalled.get());
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void doFilter_whenWorkspaceHeaderMissing_preservesAuthenticatedWorkspaceDuringRequest() throws Exception {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setWorkspaceId("workspace-a");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.addHeader("X-Tenant-Id", "tenant-a");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean sawWorkspaceInChain = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> {
            sawWorkspaceInChain.set("workspace-a".equals(TenantContext.getWorkspaceId()));
        };

        tenantFilter.doFilter(request, response, chain);

        assertTrue(sawWorkspaceInChain.get());
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void doFilter_whenAuthenticatedJwtHasNoWorkspace_rejectsWorkspaceHeaderAdoption() throws Exception {
        TenantContext.setTenantId("tenant-a");
        UserPrincipal principal = new UserPrincipal("user-1", "tenant-a", null, null, Set.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.Collections.emptyList()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-Workspace-Id", "workspace-b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(!chainCalled.get());
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void doFilter_whenHeaderConflictsWithAuthenticatedEnvironment_returnsForbiddenAndClearsContext() throws Exception {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setEnvironmentId("prod");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-Environment-Id", "staging");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(!chainCalled.get());
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getEnvironmentId());
    }

    @Test
    void doFilter_onTenantFreePath_allowsRequestWithoutTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    void doFilter_onSsoPath_allowsRequestWithoutTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sso/metadata");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    void doFilter_onScimPath_allowsRequestWithoutTenant() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scim/v2/Users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);

        tenantFilter.doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }
}
