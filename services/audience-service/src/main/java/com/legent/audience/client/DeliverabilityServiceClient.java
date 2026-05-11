package com.legent.audience.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Locale;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;
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
        if (emails == null || emails.isEmpty()) {
            return Collections.emptySet();
        }

        try {
            // Call deliverability-service to get suppression list
            JsonNode response = webClient.get()
                    .uri("/api/v1/deliverability/suppressions/internal")
                    .header("X-Tenant-Id", tenantId)
                    .header("X-Workspace-Id", workspaceId)
                    .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                    .header("X-Internal-Token", internalApiToken)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse -> Mono.error(new RuntimeException("Failed to fetch suppressions: " + clientResponse.statusCode()))
                    )
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("Empty suppression list response, proceeding without suppression check");
                return Collections.emptySet();
            }

            JsonNode data = response.get("data");
            if (!data.isArray()) {
                return Collections.emptySet();
            }

            // Extract suppressed emails from the response
            Set<String> suppressedEmails = StreamSupport.stream(data.spliterator(), false)
                    .map(node -> node.has("email") ? node.get("email").asText() : null)
                    .filter(email -> email != null && !email.isBlank())
                    .filter(emails::contains) // Only include emails in our check list
                    .collect(Collectors.toSet());

            log.debug("Found {} suppressed emails out of {} checked", suppressedEmails.size(), emails.size());
            return suppressedEmails;

        } catch (Exception e) {
            log.error("Error checking suppressed emails: {}", e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("403")) {
                log.error("Suppression sync auth failed with 403. Failing closed for tenant {} workspace {} and suppressing {} recipients",
                        tenantId, workspaceId, emails.size());
                return new HashSet<>(emails);
            }
            return Collections.emptySet();
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
