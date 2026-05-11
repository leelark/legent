package com.legent.common.config;


import com.legent.common.util.IdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.legent.common.constant.AppConstants.*;

/**
 * Filter that injects a correlation ID into every request for
 * distributed tracing. Sets it in both MDC (for logging) and
 * the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_TENANT_ID = "tenantId";

    @Override
    protected void doFilterInternal(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull FilterChain filterChain) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);
        String requestId = resolveRequestId(request, correlationId);
        String tenantId = request.getHeader(HEADER_TENANT_ID);

        try {
            MDC.put(MDC_CORRELATION_ID, correlationId);
            MDC.put(MDC_REQUEST_ID, requestId);
            if (tenantId != null) {
                MDC.put(MDC_TENANT_ID, tenantId);
            }

            response.setHeader(HEADER_CORRELATION_ID, correlationId);
            response.setHeader(HEADER_REQUEST_ID, requestId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TENANT_ID);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER_CORRELATION_ID);
        return (incoming != null && !incoming.isBlank()) ? incoming : IdGenerator.newCorrelationId();
    }

    private String resolveRequestId(HttpServletRequest request, String correlationId) {
        String incoming = request.getHeader(HEADER_REQUEST_ID);
        return (incoming != null && !incoming.isBlank()) ? incoming : correlationId;
    }
}
