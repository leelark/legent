package com.legent.campaign.client;

import com.legent.common.constant.AppConstants;
import com.legent.common.event.EmailContentReference;
import com.legent.common.security.InternalApiTokenValidator;
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

    public EmailContentReference createRenderedContentReference(RenderedContentSnapshotRequest request,
                                                                boolean inlineFallbackIncluded) {
        if (request == null) {
            throw new ContentServiceException("Rendered content snapshot request is required");
        }
        String scopedTenantId = requireText("tenantId", request.tenantId());
        String scopedWorkspaceId = requireWorkspaceId(request.workspaceId());
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("tenantId", scopedTenantId);
            payload.put("workspaceId", scopedWorkspaceId);
            payload.put("campaignId", requireText("campaignId", request.campaignId()));
            payload.put("jobId", requireText("jobId", request.jobId()));
            payload.put("batchId", requireText("batchId", request.batchId()));
            payload.put("messageId", requireText("messageId", request.messageId()));
            payload.put("contentId", requireText("contentId", request.contentId()));
            payload.put("subject", requireText("subject", request.subject()));
            payload.put("htmlBody", requireText("htmlBody", request.htmlBody()));
            payload.put("textBody", request.textBody() == null ? "" : request.textBody());
            payload.put("inlineFallbackIncluded", inlineFallbackIncluded);
            Map<String, Object> response = webClient.post()
                    .uri("/api/v1/content/rendered-content/internal")
                    .headers(headers -> scopedHeaders(headers, scopedTenantId, scopedWorkspaceId, true))
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(READ_TIMEOUT)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                            .doAfterRetry(sig -> log.warn("Retrying rendered content snapshot create for {}/{}",
                                    scopedTenantId, request.messageId())))
                    .block();
            Map<String, Object> data = responseData(response, "rendered content snapshot");
            return toEmailContentReference(data);
        } catch (Exception e) {
            log.error("Failed to create rendered content snapshot for tenant {} message {}",
                    scopedTenantId,
                    request.messageId(),
                    e);
            throw new ContentServiceException("Failed to create rendered content snapshot for message " + request.messageId(), e);
        }
    }

    public StoredRenderedContent readRenderedContentReference(String tenantId,
                                                             String workspaceId,
                                                             String referenceId) {
        String scopedTenantId = requireText("tenantId", tenantId);
        String scopedWorkspaceId = requireWorkspaceId(workspaceId);
        String scopedReferenceId = requireText("referenceId", referenceId);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/v1/content/rendered-content/{referenceId}/internal", scopedReferenceId)
                    .headers(headers -> scopedHeaders(headers, scopedTenantId, scopedWorkspaceId, true))
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(READ_TIMEOUT)
                    .block();
            Map<String, Object> data = responseData(response, "rendered content snapshot");
            return new StoredRenderedContent(
                    stringValue(data.get("subject")),
                    stringValue(data.get("htmlBody")),
                    stringValue(data.get("textBody")),
                    dataToStringMap(data));
        } catch (Exception e) {
            throw new ContentServiceException("Failed to read rendered content snapshot " + scopedReferenceId, e);
        }
    }

    public SendGovernancePolicySummary getSendGovernancePolicy(String tenantId,
                                                               String workspaceId,
                                                               String policyId) {
        String scopedTenantId = requireText("tenantId", tenantId);
        String scopedWorkspaceId = requireWorkspaceId(workspaceId);
        String scopedPolicyId = requireText("policyId", policyId);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/v1/content/send-governance-policies/{policyId}/internal", scopedPolicyId)
                    .headers(headers -> scopedHeaders(headers, scopedTenantId, scopedWorkspaceId, true))
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .timeout(READ_TIMEOUT)
                    .block();
            Map<String, Object> data = responseData(response, "send governance policy");
            return new SendGovernancePolicySummary(
                    requireResponseText(data, "id"),
                    stringValue(data.get("policyKey")),
                    stringValue(data.get("classification")),
                    booleanValue(data.get("commercial")),
                    stringValue(data.get("senderProfileId")),
                    stringValue(data.get("deliveryProfileId")),
                    stringValue(data.get("sendingDomain")),
                    stringValue(data.get("providerId")),
                    stringValue(data.get("unsubscribePolicy")),
                    booleanValue(data.get("suppressionRequired")),
                    booleanValue(data.get("consentRequired")),
                    booleanValue(data.get("trackingAllowed")),
                    intValue(data.get("sendLogRetentionDays")),
                    stringValue(data.get("publicationPolicy")),
                    longValue(data.get("version")),
                    booleanValue(data.get("active")));
        } catch (Exception e) {
            throw new ContentServiceException("Failed to read send governance policy " + scopedPolicyId, e);
        }
    }

    private Map<String, Object> responseData(Map<String, Object> response, String label) {
        if (response == null || !response.containsKey("data") || !(response.get("data") instanceof Map<?, ?> raw)) {
            throw new ContentServiceException("Invalid response from content-service for " + label);
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        raw.forEach((key, value) -> data.put(String.valueOf(key), value));
        return data;
    }

    private EmailContentReference toEmailContentReference(Map<String, Object> data) {
        EmailContentReference reference = new EmailContentReference();
        reference.setReferenceId(stringValue(data.get("referenceId")));
        reference.setStorageBackend(stringValue(data.get("storageBackend")));
        reference.setTenantId(stringValue(data.get("tenantId")));
        reference.setWorkspaceId(stringValue(data.get("workspaceId")));
        reference.setCampaignId(stringValue(data.get("campaignId")));
        reference.setJobId(stringValue(data.get("jobId")));
        reference.setBatchId(stringValue(data.get("batchId")));
        reference.setMessageId(stringValue(data.get("messageId")));
        reference.setContentId(stringValue(data.get("contentId")));
        reference.setSubjectSha256(stringValue(data.get("subjectSha256")));
        reference.setHtmlSha256(stringValue(data.get("htmlSha256")));
        reference.setTextSha256(stringValue(data.get("textSha256")));
        reference.setSubjectBytes(intValue(data.get("subjectBytes")));
        reference.setHtmlBytes(intValue(data.get("htmlBytes")));
        reference.setTextBytes(intValue(data.get("textBytes")));
        reference.setCreatedAt(instantValue(data.get("createdAt")));
        reference.setExpiresAt(instantValue(data.get("expiresAt")));
        reference.setInlineFallbackIncluded(booleanValue(data.get("inlineFallbackIncluded")));
        if (reference.getReferenceId() == null || reference.getStorageBackend() == null) {
            throw new ContentServiceException("Content-service returned incomplete rendered content reference");
        }
        return reference;
    }

    private Map<String, String> dataToStringMap(Map<String, Object> data) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        data.forEach((key, value) -> {
            if (value != null && !(value instanceof Map<?, ?>)) {
                result.put(key, String.valueOf(value));
            }
        });
        Object metadata = data.get("metadata");
        if (metadata instanceof Map<?, ?> raw) {
            raw.forEach((key, value) -> {
                if (value != null) {
                    result.put(String.valueOf(key), String.valueOf(value));
                }
            });
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.parseInt(value.toString());
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(value.toString());
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.parseBoolean(value.toString());
    }

    private java.time.Instant instantValue(Object value) {
        if (value instanceof java.time.Instant instant) {
            return instant;
        }
        return value == null ? null : java.time.Instant.parse(value.toString());
    }

    public record RenderedContentSnapshotRequest(String tenantId,
                                                 String workspaceId,
                                                 String campaignId,
                                                 String jobId,
                                                 String batchId,
                                                 String messageId,
                                                 String contentId,
                                                 String subject,
                                                 String htmlBody,
                                                 String textBody) {}

    public record StoredRenderedContent(String subject,
                                        String htmlBody,
                                        String textBody,
                                        Map<String, String> metadata) {}

    public record SendGovernancePolicySummary(String id,
                                              String policyKey,
                                              String classification,
                                              Boolean commercial,
                                              String senderProfileId,
                                              String deliveryProfileId,
                                              String sendingDomain,
                                              String providerId,
                                              String unsubscribePolicy,
                                              Boolean suppressionRequired,
                                              Boolean consentRequired,
                                              Boolean trackingAllowed,
                                              Integer sendLogRetentionDays,
                                              String publicationPolicy,
                                              Long version,
                                              Boolean active) {}

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
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", token);
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

    private String requireResponseText(Map<String, Object> data, String field) {
        String value = stringValue(data.get(field));
        if (value == null || value.isBlank()) {
            throw new ContentServiceException("Content-service returned incomplete send governance policy: " + field);
        }
        return value.trim();
    }
}
