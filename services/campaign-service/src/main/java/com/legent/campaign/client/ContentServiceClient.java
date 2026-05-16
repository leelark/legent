package com.legent.campaign.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Client for calling content-service to fetch and render templates.
 * Configured with timeouts and retry policies for resilience.
 */
@Slf4j
@Component
public class ContentServiceClient {

    private final WebClient webClient;
    private final String internalApiToken;
    
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

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
     * Fetches a template by ID from content-service.
     * @throws ContentServiceException if the template cannot be fetched
     */
    public TemplateDto getTemplate(String tenantId, String templateId) {
        try {
            java.util.Map<String, Object> response = webClient.get()
                    .uri("/api/v1/templates/{id}", templateId)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                            .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound))
                            .doAfterRetry(sig -> log.warn("Retrying template fetch for {}/{}", tenantId, templateId)))
                    .block();

            if (response == null || !response.containsKey("data")) {
                throw new ContentServiceException("Invalid response from content-service for template " + templateId);
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
            String id = data.get("id") != null ? data.get("id").toString() : null;
            String name = data.get("name") != null ? data.get("name").toString() : null;
            String subject = data.get("subject") != null ? data.get("subject").toString() : null;
            return new TemplateDto(id, name, subject);
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Template {} not found for tenant {}", templateId, tenantId);
            throw new ContentServiceNotFoundException("Template " + templateId + " not found for tenant " + tenantId, e);
        } catch (Exception e) {
            log.error("Failed to fetch template {} for tenant {}", templateId, tenantId, e);
            throw new ContentServiceException("Failed to fetch template " + templateId + " for tenant " + tenantId, e);
        }
    }

    /**
     * Renders a template with personalization variables.
     * @throws ContentServiceException if the template cannot be rendered
     */
    public RenderedContent renderTemplate(String tenantId, String templateId, Map<String, Object> variables) {
        try {
            java.util.Map<String, Object> response = webClient.post()
                    .uri("/api/v1/content/{templateId}/render/internal", templateId)
                    .header("X-Tenant-Id", tenantId)
                    .header("X-Internal-Token", internalApiToken)
                    .bodyValue(variables != null ? variables : Map.of())
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                            .doAfterRetry(sig -> log.warn("Retrying template render for {}/{}", tenantId, templateId)))
                    .block();

            if (response == null || !response.containsKey("data")) {
                throw new ContentServiceException("Invalid response from content-service for render " + templateId);
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
            String subject = data.get("subject") != null ? data.get("subject").toString() : null;
            String htmlBody = data.get("htmlBody") != null ? data.get("htmlBody").toString() : null;
            String textBody = data.get("textBody") != null ? data.get("textBody").toString() : null;
            return new RenderedContent(subject, htmlBody, textBody);
        } catch (Exception e) {
            log.error("Failed to render template {} for tenant {}", templateId, tenantId, e);
            throw new ContentServiceException("Failed to render template " + templateId + " for tenant " + tenantId, e);
        }
    }

    /**
     * Gets the latest published version of a template.
     * @throws ContentServiceException if the version cannot be fetched
     */
    public TemplateVersionDto getLatestVersion(String tenantId, String templateId) {
        try {
            java.util.Map<String, Object> response = webClient.get()
                    .uri("/api/v1/templates/{id}/versions/latest", templateId)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                            .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound))
                            .doAfterRetry(sig -> log.warn("Retrying latest version fetch for {}/{}", tenantId, templateId)))
                    .block();

            if (response == null || !response.containsKey("data")) {
                throw new ContentServiceException("Invalid response from content-service for latest version " + templateId);
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
            String subject = data.get("subject") != null ? data.get("subject").toString() : null;
            String htmlContent = data.get("htmlContent") != null ? data.get("htmlContent").toString() : null;
            String textContent = data.get("textContent") != null ? data.get("textContent").toString() : null;
            return new TemplateVersionDto(subject, htmlContent, textContent);
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Latest version not found for template {} tenant {}", templateId, tenantId);
            throw new ContentServiceNotFoundException("Latest version not found for template " + templateId + " tenant " + tenantId, e);
        } catch (Exception e) {
            log.error("Failed to get latest version for template {} tenant {}", templateId, tenantId, e);
            throw new ContentServiceException("Failed to get latest version for template " + templateId + " tenant " + tenantId, e);
        }
    }

    public record TemplateDto(String id, String name, String subject) {}
    public record TemplateVersionDto(String subject, String htmlContent, String textContent) {}
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

    /**
     * Exception thrown when content is not found in content-service.
     */
    public static class ContentServiceNotFoundException extends ContentServiceException {
        public ContentServiceNotFoundException(String message, Throwable cause) {
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
}
