package com.legent.campaign.client;

import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Client for calling content-service to render templates for campaign sends.
 * Configured with timeouts and retry policies for resilience.
 */
@Slf4j
@Component
public class ContentServiceClient {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String internalApiToken;

    public ContentServiceClient(
            @Value("${legent.content-service.url:http://content-service:8090}") String baseUrl,
            @Value("${legent.internal.api-token}") String internalApiToken) {
        validateInternalApiToken(internalApiToken);
        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(READ_TIMEOUT)
                .compress(true);
        
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.internalApiToken = internalApiToken;
    }

    /**
     * Renders a template with personalization variables.
     * @throws ContentServiceException if the template cannot be rendered
     */
    public RenderedContent renderTemplate(String tenantId, String templateId, Map<String, Object> variables) {
        return renderTemplate(tenantId, requireWorkspaceContext(), templateId, variables);
    }

    public RenderedContent renderTemplate(String tenantId, String workspaceId, String templateId, Map<String, Object> variables) {
        String scopedTenantId = requireText("tenantId", tenantId);
        String scopedWorkspaceId = requireWorkspaceId(workspaceId);
        String scopedTemplateId = requireText("templateId", templateId);
        try {
            java.util.Map<String, Object> response = webClient.post()
                    .uri("/api/v1/content/{templateId}/render/internal", scopedTemplateId)
                    .headers(headers -> scopedHeaders(headers, scopedTenantId, scopedWorkspaceId, true))
                    .bodyValue(variables != null ? variables : Map.of())
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                            .doAfterRetry(sig -> log.warn("Retrying template render for {}/{}", scopedTenantId, scopedTemplateId)))
                    .block();

            if (response == null || !response.containsKey("data")) {
                throw new ContentServiceException("Invalid response from content-service for render " + scopedTemplateId);
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
            String subject = data.get("subject") != null ? data.get("subject").toString() : null;
            String htmlBody = data.get("htmlBody") != null ? data.get("htmlBody").toString() : null;
            String textBody = data.get("textBody") != null ? data.get("textBody").toString() : null;
            return new RenderedContent(subject, htmlBody, textBody);
        } catch (Exception e) {
            log.error("Failed to render template {} for tenant {}", scopedTemplateId, scopedTenantId, e);
            throw new ContentServiceException("Failed to render template " + scopedTemplateId + " for tenant " + scopedTenantId, e);
        }
    }

    public record RenderedContent(String subject, String htmlBody, String textBody) {}

    /**
     * Exception thrown when content-service operations fail.
     */
    public static class ContentServiceException extends RuntimeException {
        public ContentServiceException(String message) {
            super(message);
        }
        public ContentServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void validateInternalApiToken(String token) {
        if (token == null || token.isBlank() || isPlaceholderToken(token)) {
            throw new IllegalStateException("legent.internal.api-token must be configured with a non-placeholder secret");
        }
    }

    private boolean isPlaceholderToken(String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("dev-token")
                || normalized.contains("change_me")
                || normalized.contains("changeme")
                || normalized.contains("replace_in_production")
                || normalized.equals("password");
    }

    private void scopedHeaders(HttpHeaders headers, String tenantId, String workspaceId, boolean internal) {
        headers.set(AppConstants.HEADER_TENANT_ID, tenantId);
        headers.set(AppConstants.HEADER_WORKSPACE_ID, workspaceId);
        setOptionalHeader(headers, AppConstants.HEADER_ENVIRONMENT_ID, TenantContext.getEnvironmentId());
        setOptionalHeader(headers, AppConstants.HEADER_REQUEST_ID, TenantContext.getRequestId());
        setOptionalHeader(headers, AppConstants.HEADER_CORRELATION_ID, TenantContext.getCorrelationId());
        if (internal) {
            headers.set("X-Internal-Token", internalApiToken);
        }
    }

    private void setOptionalHeader(HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    private String requireWorkspaceContext() {
        try {
            return TenantContext.requireWorkspaceId();
        } catch (IllegalStateException e) {
            throw new ContentServiceException("Workspace context is required for content-service workspace-scoped API calls", e);
        }
    }

    private String requireWorkspaceId(String workspaceId) {
        return requireText("workspaceId", workspaceId);
    }

    private String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ContentServiceException(field + " is required for content-service calls");
        }
        return value.trim();
    }
}
