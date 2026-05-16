package com.legent.platform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.security.OutboundUrlGuard;
import com.legent.platform.domain.WebhookConfig;
import com.legent.platform.repository.WebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import com.legent.security.TenantContext;

@RestController
@RequestMapping({"/api/v1/platform/webhooks", "/api/v1/admin/webhooks"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')")
public class WebhookController {

    private static final int MAX_EVENTS_SUBSCRIBED = 50;
    private static final int MAX_EVENT_NAME_LENGTH = 128;
    private static final int MIN_SECRET_KEY_LENGTH = 32;
    private static final Pattern EVENT_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*){1,5}");
    private static final Set<String> SUPPORTED_WEBHOOK_EVENTS = Set.of(
            AppConstants.TOPIC_SYSTEM_INITIALIZED,
            AppConstants.TOPIC_CONFIG_UPDATED,
            AppConstants.TOPIC_TENANT_BOOTSTRAP_COMPLETED,
            AppConstants.TOPIC_SUBSCRIBER_CREATED,
            AppConstants.TOPIC_SUBSCRIBER_UPDATED,
            AppConstants.TOPIC_SUBSCRIBER_DELETED,
            AppConstants.TOPIC_SEGMENT_CREATED,
            AppConstants.TOPIC_SEGMENT_UPDATED,
            AppConstants.TOPIC_SEGMENT_RECOMPUTED,
            AppConstants.TOPIC_IMPORT_STARTED,
            AppConstants.TOPIC_IMPORT_COMPLETED,
            AppConstants.TOPIC_IMPORT_FAILED,
            AppConstants.TOPIC_SEND_REQUESTED,
            AppConstants.TOPIC_AUDIENCE_RESOLVED,
            AppConstants.TOPIC_SEND_PROCESSING,
            AppConstants.TOPIC_BATCH_CREATED,
            AppConstants.TOPIC_BATCH_COMPLETED,
            AppConstants.TOPIC_SEND_COMPLETED,
            AppConstants.TOPIC_SEND_FAILED,
            AppConstants.TOPIC_CONTENT_PUBLISHED,
            AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
            AppConstants.TOPIC_EMAIL_SENT,
            AppConstants.TOPIC_EMAIL_FAILED,
            AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED,
            AppConstants.TOPIC_EMAIL_BOUNCED,
            AppConstants.TOPIC_EMAIL_COMPLAINT,
            AppConstants.TOPIC_EMAIL_UNSUBSCRIBED,
            AppConstants.TOPIC_EMAIL_OPEN,
            AppConstants.TOPIC_EMAIL_CLICK,
            AppConstants.TOPIC_EMAIL_DELIVERED,
            AppConstants.TOPIC_CONVERSION_EVENT,
            AppConstants.TOPIC_TRACKING_INGESTED,
            AppConstants.TOPIC_ANALYTICS_AGGREGATED,
            AppConstants.TOPIC_WORKFLOW_STARTED,
            AppConstants.TOPIC_WORKFLOW_STEP_STARTED,
            AppConstants.TOPIC_WORKFLOW_STEP_COMPLETED,
            AppConstants.TOPIC_WORKFLOW_STEP_FAILED,
            AppConstants.TOPIC_WORKFLOW_COMPLETED,
            AppConstants.TOPIC_DOMAIN_VERIFIED,
            AppConstants.TOPIC_REPUTATION_UPDATED,
            AppConstants.TOPIC_BOUNCE_CLASSIFIED,
            AppConstants.TOPIC_COMPLAINT_RECEIVED,
            AppConstants.TOPIC_SUPPRESSION_UPDATED,
            AppConstants.TOPIC_SPAM_SCORE_GENERATED,
            AppConstants.TOPIC_COMPLIANCE_VIOLATION,
            AppConstants.TOPIC_SEARCH_INDEX_UPDATED,
            AppConstants.TOPIC_NOTIFICATION_CREATED,
            AppConstants.TOPIC_INTEGRATION_SYNC,
            AppConstants.TOPIC_IDENTITY_USER_SIGNUP
    );

    private final WebhookConfigRepository webhookRepository;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ApiResponse<List<WebhookConfig>> listWebhooks() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(webhookRepository.findByTenantIdAndWorkspaceIdAndIsActiveTrue(tenantId, workspaceId));
    }

    @PostMapping
    public ApiResponse<WebhookConfig> createWebhook(@RequestBody WebhookConfig hook) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        validateWebhookForCreate(hook);
        hook.setId(UUID.randomUUID().toString());
        hook.setTenantId(tenantId);
        hook.setWorkspaceId(workspaceId);
        return ApiResponse.ok(webhookRepository.save(hook));
    }

    private void validateWebhookForCreate(WebhookConfig hook) {
        if (hook == null) {
            throw badRequest("Webhook payload is required");
        }

        URI endpoint;
        try {
            endpoint = OutboundUrlGuard.requirePublicHttpsUri(hook.getEndpointUrl(), "webhook endpoint");
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
        hook.setEndpointUrl(endpoint.toString());

        hook.setEventsSubscribed(canonicalizeSubscribedEvents(hook.getEventsSubscribed()));
        hook.setSecretKey(validateAndNormalizeSecretKey(hook.getSecretKey()));
    }

    private String canonicalizeSubscribedEvents(String rawEvents) {
        String raw = rawEvents == null ? "" : rawEvents.trim();
        if (raw.isBlank()) {
            throw badRequest("Webhook eventsSubscribed must include at least one event");
        }

        List<String> events = parseSubscribedEvents(raw).stream()
                .map(this::normalizeEvent)
                .filter(event -> event != null)
                .distinct()
                .toList();
        if (events.isEmpty()) {
            throw badRequest("Webhook eventsSubscribed must include at least one event");
        }
        if (events.size() > MAX_EVENTS_SUBSCRIBED) {
            throw badRequest("Webhook eventsSubscribed cannot include more than " + MAX_EVENTS_SUBSCRIBED + " events");
        }
        for (String event : events) {
            validateSupportedEvent(event);
        }

        try {
            return objectMapper.writeValueAsString(events);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize webhook events", e);
        }
    }

    private List<String> parseSubscribedEvents(String rawEvents) {
        if (looksLikeJson(rawEvents)) {
            try {
                JsonNode node = objectMapper.readTree(rawEvents);
                if (!node.isArray()) {
                    throw badRequest("Webhook eventsSubscribed must be a JSON array of event names");
                }

                List<String> events = new ArrayList<>();
                for (JsonNode eventNode : node) {
                    if (!eventNode.isTextual()) {
                        throw badRequest("Webhook eventsSubscribed must contain only event name strings");
                    }
                    events.add(eventNode.asText());
                }
                return events;
            } catch (JsonProcessingException e) {
                throw badRequest("Webhook eventsSubscribed must be a valid JSON array of event names");
            }
        }

        return Arrays.asList(rawEvents.split(","));
    }

    private boolean looksLikeJson(String rawEvents) {
        return rawEvents.startsWith("[") || rawEvents.startsWith("{") || rawEvents.startsWith("\"");
    }

    private String normalizeEvent(String event) {
        if (event == null) {
            return null;
        }
        String normalized = event.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private void validateSupportedEvent(String event) {
        if (event.length() > MAX_EVENT_NAME_LENGTH || !EVENT_NAME_PATTERN.matcher(event).matches()) {
            throw badRequest("Webhook eventsSubscribed contains a malformed event name: " + event);
        }
        if (!SUPPORTED_WEBHOOK_EVENTS.contains(event)) {
            throw badRequest("Webhook eventsSubscribed contains an unsupported event name: " + event);
        }
    }

    private String validateAndNormalizeSecretKey(String rawSecretKey) {
        if (rawSecretKey == null || rawSecretKey.isBlank()) {
            throw badRequest("Webhook secretKey is required");
        }
        String secretKey = rawSecretKey.trim();
        if (secretKey.length() < MIN_SECRET_KEY_LENGTH || !hasSecretDiversity(secretKey)) {
            throw badRequest("Webhook secretKey must be at least " + MIN_SECRET_KEY_LENGTH
                    + " characters and include high-entropy mixed characters");
        }
        return secretKey;
    }

    private boolean hasSecretDiversity(String secretKey) {
        long characterClasses = 0;
        characterClasses += secretKey.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        characterClasses += secretKey.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        characterClasses += secretKey.chars().anyMatch(Character::isDigit) ? 1 : 0;
        characterClasses += secretKey.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch)) ? 1 : 0;
        long distinctCharacters = secretKey.chars().distinct().count();
        return characterClasses >= 3 && distinctCharacters >= 16;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
