package com.legent.common.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static com.legent.common.constant.AppConstants.HEADER_CORRELATION_ID;
import static com.legent.common.constant.AppConstants.HEADER_REQUEST_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void doFilter_setsCorrelationAndRequestHeadersAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(HEADER_CORRELATION_ID, "corr-1");
        request.addHeader(HEADER_REQUEST_ID, "req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                requestIdInChain.set(MDC.get("requestId")));

        assertEquals("corr-1", response.getHeader(HEADER_CORRELATION_ID));
        assertEquals("req-1", response.getHeader(HEADER_REQUEST_ID));
        assertEquals("req-1", requestIdInChain.get());
        assertNull(MDC.get("requestId"));
    }
}
