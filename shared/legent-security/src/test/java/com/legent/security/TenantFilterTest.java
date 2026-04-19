package com.legent.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantFilterTest {

    private final TenantFilter tenantFilter = new TenantFilter();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
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
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean sawTenantInChain = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> {
            sawTenantInChain.set("tenant-1".equals(TenantContext.getTenantId()));
        };

        tenantFilter.doFilter(request, response, chain);

        assertTrue(sawTenantInChain.get());
        assertNull(TenantContext.getTenantId());
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
}
