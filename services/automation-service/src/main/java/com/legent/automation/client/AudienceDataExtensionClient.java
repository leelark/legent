package com.legent.automation.client;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.security.InternalApiTokenValidator;
import com.legent.common.security.InternalServiceIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;

@Component
public class AudienceDataExtensionClient {

    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final String SERVICE_NAME = "automation-service";

    private final RestClient restClient;
    private final String internalApiToken;

    public AudienceDataExtensionClient(@Value("${legent.audience-service.url:}") String audienceServiceUrl,
                                       @Value("${legent.internal.api-token:}") String internalApiToken) {
        String baseUrl = trimToNull(audienceServiceUrl);
        this.restClient = baseUrl == null ? null : RestClient.builder().baseUrl(baseUrl).build();
        this.internalApiToken = internalApiToken;
    }

    public Map<String, Object> runSqlQueryActivity(String tenantId,
                                                   String workspaceId,
                                                   Map<String, Object> request) {
        if (restClient == null) {
            throw new IllegalStateException("audience-service URL is not configured");
        }
        String token = InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
        try {
            ApiResponse<Map<String, Object>> response = restClient.post()
                    .uri("/api/v1/data-extensions/query-activities/internal")
                    .header(AppConstants.HEADER_TENANT_ID, tenantId)
                    .header(AppConstants.HEADER_WORKSPACE_ID, workspaceId)
                    .header("X-Internal-Token", token)
                    .headers(headers -> addInternalSignatureHeaders(
                            headers,
                            token,
                            tenantId,
                            workspaceId,
                            InternalServiceIdentity.ACTION_DATA_EXTENSION_QUERY_ACTIVITY))
                    .body(request)
                    .retrieve()
                    .body(MAP_RESPONSE_TYPE);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                String message = response == null || response.getError() == null
                        ? "audience-service returned empty SQL activity response"
                        : response.getError().getMessage();
                throw new IllegalStateException(message);
            }
            return new LinkedHashMap<>(response.getData());
        } catch (RestClientException ex) {
            throw new IllegalStateException("audience-service SQL activity request failed", ex);
        }
    }

    public Map<String, Object> startImportActivity(String tenantId,
                                                   String workspaceId,
                                                   Map<String, Object> request) {
        if (restClient == null) {
            throw new IllegalStateException("audience-service URL is not configured");
        }
        String token = InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
        try {
            ApiResponse<Map<String, Object>> response = restClient.post()
                    .uri("/api/v1/imports/internal/start")
                    .header(AppConstants.HEADER_TENANT_ID, tenantId)
                    .header(AppConstants.HEADER_WORKSPACE_ID, workspaceId)
                    .header("X-Internal-Token", token)
                    .headers(headers -> addInternalSignatureHeaders(
                            headers,
                            token,
                            tenantId,
                            workspaceId,
                            InternalServiceIdentity.ACTION_AUDIENCE_IMPORT_START))
                    .body(request)
                    .retrieve()
                    .body(MAP_RESPONSE_TYPE);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                String message = response == null || response.getError() == null
                        ? "audience-service returned empty import activity response"
                        : response.getError().getMessage();
                throw new IllegalStateException(message);
            }
            return new LinkedHashMap<>(response.getData());
        } catch (RestClientException ex) {
            throw new IllegalStateException("audience-service import activity request failed", ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void addInternalSignatureHeaders(org.springframework.http.HttpHeaders headers,
                                             String token,
                                             String tenantId,
                                             String workspaceId,
                                             String action) {
        Instant timestamp = Instant.now();
        headers.set(InternalServiceIdentity.HEADER_SERVICE, SERVICE_NAME);
        headers.set(InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, timestamp.toString());
        headers.set(InternalServiceIdentity.HEADER_SIGNATURE, InternalServiceIdentity.sign(
                token,
                SERVICE_NAME,
                tenantId,
                workspaceId,
                action,
                timestamp));
    }
}
