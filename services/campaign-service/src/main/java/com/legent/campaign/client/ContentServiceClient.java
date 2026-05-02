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

import java.time.Duration;
import java.util.Map;

/**
 * Client for calling content-service to fetch and render templates.
 * Configured with timeouts and retry policies for resilience.
 */
@Slf4j
@Component
public class ContentServiceClient {

    private final WebClient webClient;
    
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    public ContentServiceClient(
            @Value("${legent.content-service.url:http://content-service:8090}") String baseUrl) {
        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(READ_TIMEOUT)
                .compress(true);
        
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Fetches a template by ID from content-service.
     */
    public TemplateDto getTemplate(String tenantId, String templateId) {
        try {
            java.util.Map<String, Object> response = webClient.get()
                    .uri("/api/v1/templates/{id}", templateId)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response == null || !response.containsKey("data")) {
                return null;
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
            String id = data.get("id") != null ? data.get("id").toString() : null;
            String name = data.get("name") != null ? data.get("name").toString() : null;
            String subject = data.get("subject") != null ? data.get("subject").toString() : null;
            return new TemplateDto(id, name, subject);
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Template {} not found for tenant {}", templateId, tenantId);
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch template {} for tenant {}", templateId, tenantId, e);
            return null;
        }
    }

    /**
     * Renders a template with personalization variables.
     */
    public RenderedContent renderTemplate(String tenantId, String templateId, Map<String, Object> variables) {
        try {
            java.util.Map<String, Object> response = webClient.post()
                    .uri("/api/v1/content/{templateId}/render", templateId)
                    .header("X-Tenant-Id", tenantId)
                    .bodyValue(variables != null ? variables : Map.of())
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response == null || !response.containsKey("data")) {
                return null;
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
            String subject = data.get("subject") != null ? data.get("subject").toString() : null;
            String htmlBody = data.get("htmlBody") != null ? data.get("htmlBody").toString() : null;
            String textBody = data.get("textBody") != null ? data.get("textBody").toString() : null;
            return new RenderedContent(subject, htmlBody, textBody);
        } catch (Exception e) {
            log.error("Failed to render template {} for tenant {}", templateId, tenantId, e);
            return null;
        }
    }

    /**
     * Gets the latest published version of a template.
     */
    public TemplateVersionDto getLatestVersion(String tenantId, String templateId) {
        try {
            java.util.Map<String, Object> response = webClient.get()
                    .uri("/api/v1/templates/{id}/versions/latest", templateId)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response == null || !response.containsKey("data")) {
                return null;
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
            String subject = data.get("subject") != null ? data.get("subject").toString() : null;
            String htmlContent = data.get("htmlContent") != null ? data.get("htmlContent").toString() : null;
            String textContent = data.get("textContent") != null ? data.get("textContent").toString() : null;
            return new TemplateVersionDto(subject, htmlContent, textContent);
        } catch (Exception e) {
            log.error("Failed to get latest version for template {} tenant {}", templateId, tenantId, e);
            return null;
        }
    }

    public record TemplateDto(String id, String name, String subject) {}
    public record TemplateVersionDto(String subject, String htmlContent, String textContent) {}
    public record RenderedContent(String subject, String htmlBody, String textBody) {}
}
