package com.legent.audience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.legent.common.security.InternalApiTokenValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Client for calling deliverability-service to check suppression status.
 */
@Slf4j
@Component
public class DeliverabilityServiceClient {

    private final WebClient webClient;
    private final String internalApiToken;

    public DeliverabilityServiceClient(
            // LEGENT-HIGH-005: Fixed port from 8085 (tracking-service) to 8087 (deliverability-service)
            @Value("${legent.deliverability-service.url:http://deliverability-service:8087}") String baseUrl,
            @Value("${legent.internal.api-token}") String internalApiToken) {
        validateInternalApiToken(internalApiToken);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.internalApiToken = internalApiToken;
    }

    /**
     * Checks which emails are suppressed (bounced, complained, or unsubscribed).
     *
     * @param tenantId The tenant ID
     * @param emails   List of emails to check
     * @return Set of suppressed email addresses
     */
    public Set<String> checkSuppressedEmails(String tenantId, String workspaceId, List<String> emails) {
        List<String> normalizedEmails = normalizeEmailCandidates(emails);
        if (normalizedEmails.isEmpty()) {
            return Collections.emptySet();
        }

        try {
            JsonNode response = webClient.post()
                    .uri("/api/v1/deliverability/suppressions/internal/check")
                    .header("X-Tenant-Id", tenantId)
                    .header("X-Workspace-Id", workspaceId)
                    .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                    .header("X-Internal-Token", internalApiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("emails", normalizedEmails))
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse -> Mono.error(new RuntimeException("Failed to check suppressions: " + clientResponse.statusCode()))
                    )
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response == null || !response.has("data")) {
                throw new SuppressionCheckException("Suppression response did not include data");
            }

            JsonNode data = response.get("data");
            JsonNode suppressedEmailData = data.get("suppressedEmails");
            if (suppressedEmailData == null || !suppressedEmailData.isArray()) {
                throw new SuppressionCheckException("Suppression response data did not include suppressedEmails");
            }

            Set<String> requestedEmails = new LinkedHashSet<>(normalizedEmails);
            Set<String> suppressedEmails = StreamSupport.stream(suppressedEmailData.spliterator(), false)
                    .map(JsonNode::asText)
                    .map(DeliverabilityServiceClient::normalizeEmail)
                    .filter(email -> email != null && requestedEmails.contains(email))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            log.debug("Found {} suppressed emails out of {} checked", suppressedEmails.size(), normalizedEmails.size());
            return suppressedEmails;

        } catch (Exception e) {
            log.error("Error checking suppressed emails: {}", e.getMessage(), e);
            throw new SuppressionCheckException(
                    "Failed to check suppressed emails for tenant " + tenantId + " workspace " + workspaceId,
                    e);
        }
    }

    public static class SuppressionCheckException extends RuntimeException {
        public SuppressionCheckException(String message) {
            super(message);
        }

        public SuppressionCheckException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void validateInternalApiToken(String token) {
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", token);
    }

    private static List<String> normalizeEmailCandidates(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return Collections.emptyList();
        }
        return emails.stream()
                .map(DeliverabilityServiceClient::normalizeEmail)
                .filter(email -> email != null)
                .distinct()
                .toList();
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
