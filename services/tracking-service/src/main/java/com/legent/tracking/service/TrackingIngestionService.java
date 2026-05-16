package com.legent.tracking.service;

import java.util.Map;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.tracking.domain.RawEvent;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.repository.RawEventRepository;
import com.legent.security.TenantContext;
import eu.bitwalker.useragentutils.UserAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingIngestionService {

    private final com.legent.cache.service.CacheService cacheService;
    private final RawEventRepository rawEventRepository;
    private final ObjectMapper objectMapper;
    private final TrackingOutboxService outboxService;

    @Transactional
    public void processOpen(String tenantId, String campaignId, String subscriberId, String messageId,
                            String workspaceId, String idempotencyKey, String userAgentString, String ipAddress) {
        processOpen(tenantId, campaignId, subscriberId, messageId, null, null, false,
                workspaceId, idempotencyKey, userAgentString, ipAddress);
    }

    @Transactional
    public void processOpen(String tenantId, String campaignId, String subscriberId, String messageId,
                            String experimentId, String variantId, boolean holdout,
                            String workspaceId, String idempotencyKey, String userAgentString, String ipAddress) {
        requireTenantAndMessage(tenantId, messageId);
        String resolvedWorkspaceId = resolveWorkspaceId(tenantId, messageId, workspaceId);
        if (isDuplicateInDatabase(tenantId, resolvedWorkspaceId, "OPEN", messageId, subscriberId) ||
            isDuplicateInRedis(tenantId, resolvedWorkspaceId, "OPEN", messageId, subscriberId, "")) {
            log.debug("Duplicate OPEN event detected for message {} subscriber {}", messageId, subscriberId);
            return;
        }
        TrackingDto.RawEventPayload payload = buildPayload("OPEN", tenantId, resolvedWorkspaceId, campaignId, subscriberId, messageId,
                experimentId, variantId, holdout, idempotencyKey, null, userAgentString, ipAddress, null);
        saveEventToDatabase(payload);
        publishEventWithOutbox(payload);
    }

    @Transactional
    public void processClick(String tenantId, String campaignId, String subscriberId, String messageId,
                             String workspaceId, String idempotencyKey, String linkUrl, String userAgentString, String ipAddress) {
        processClick(tenantId, campaignId, subscriberId, messageId, null, null, false,
                workspaceId, idempotencyKey, linkUrl, userAgentString, ipAddress);
    }

    @Transactional
    public void processClick(String tenantId, String campaignId, String subscriberId, String messageId,
                             String experimentId, String variantId, boolean holdout,
                             String workspaceId, String idempotencyKey, String linkUrl, String userAgentString, String ipAddress) {
        requireTenantAndMessage(tenantId, messageId);
        String resolvedWorkspaceId = resolveWorkspaceId(tenantId, messageId, workspaceId);
        if (isDuplicateInDatabase(tenantId, resolvedWorkspaceId, "CLICK", messageId, subscriberId) ||
            isDuplicateInRedis(tenantId, resolvedWorkspaceId, "CLICK", messageId, subscriberId, linkUrl)) {
            log.debug("Duplicate CLICK event detected for message {} subscriber {}", messageId, subscriberId);
            return;
        }
        TrackingDto.RawEventPayload payload = buildPayload("CLICK", tenantId, resolvedWorkspaceId, campaignId, subscriberId, messageId,
                experimentId, variantId, holdout, idempotencyKey, linkUrl, userAgentString, ipAddress, null);
        saveEventToDatabase(payload);
        publishEventWithOutbox(payload);
    }

    @Transactional
    public void processConversion(String tenantId, String workspaceId, String idempotencyKey, TrackingDto.ConversionRequest request, String userAgentString, String ipAddress) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (request == null || request.getEventName() == null || request.getEventName().isBlank()) {
            throw new IllegalArgumentException("eventName is required");
        }
        Map<String, Object> meta = new HashMap<>();
        meta.put("eventName", request.getEventName().trim());
        meta.put("customMetricName", request.getEventName().trim());
        meta.put("value", request.getValue());
        meta.put("currency", request.getCurrency());

        TrackingDto.RawEventPayload payload = buildPayload("CONVERSION", tenantId, workspaceId, request.getCampaignId(), request.getSubscriberId(), request.getMessageId(),
                request.getExperimentId(), request.getVariantId(), false, idempotencyKey, null, userAgentString, ipAddress, meta);
        saveEventToDatabase(payload);
        publishEventWithOutbox(payload);
    }

    /**
     * Checks Redis for recent duplicate (fast path).
     * Uses SHA-256 for URL hashing to prevent collision attacks.
     */
    private boolean isDuplicateInRedis(String tenantId, String workspaceId, String type, String msgId, String subId, String url) {
        String urlHash = "";
        if (url != null && !url.isEmpty()) {
            urlHash = ":" + sha256Hash(url);
        }
        // Use 7-day window for click deduplication, 2 hours for opens
        java.time.Duration ttl = "CLICK".equals(type) ? java.time.Duration.ofDays(7) : java.time.Duration.ofHours(2);
        String key = "track:dedup:" + tenantId + ":" + workspaceId + ":" + type + ":" + msgId + ":" + subId + urlHash;
        if (cacheService.get(key, String.class).isPresent()) {
            return true;
        }
        cacheService.set(key, "1", ttl);
        return false;
    }

    /**
     * Computes SHA-256 hash of input string.
     * Used for deduplication to prevent hash collisions (vs Java's 32-bit hashCode).
     */
    private String sha256Hash(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // Fallback to full URL if SHA-256 fails (should never happen)
            return java.util.Base64.getEncoder().encodeToString(input.getBytes());
        }
    }

    /**
     * Checks database for existing event (slower but persistent).
     * Uses the last 24 hours as the deduplication window.
     */
    private boolean isDuplicateInDatabase(String tenantId, String workspaceId, String type, String msgId, String subId) {
        if (msgId == null || subId == null) {
            return false; // Cannot deduplicate without both IDs
        }
        // Check if we already have this event in the database within last 24 hours
        Instant since = Instant.now().minusSeconds(86400); // 24 hours ago
        return rawEventRepository.findTopByTenantIdAndWorkspaceIdAndEventTypeAndMessageIdAndSubscriberIdAndTimestampAfter(
                tenantId, workspaceId, type, msgId, subId, since).isPresent();
    }

    private void requireTenantAndMessage(String tenantId, String messageId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId is required");
        }
    }

    /**
     * Saves the event to persistent storage. Fail closed so Kafka never receives
     * an event that the database transaction could not persist.
     */
    private void saveEventToDatabase(TrackingDto.RawEventPayload payload) {
        try {
            RawEvent event = new RawEvent();
            event.setId(payload.getId());
            event.setTenantId(payload.getTenantId());
            event.setWorkspaceId(payload.getWorkspaceId());
            event.setTeamId(payload.getTeamId());
            event.setOwnershipScope(payload.getOwnershipScope() == null ? "WORKSPACE" : payload.getOwnershipScope());
            event.setEventType(payload.getEventType());
            event.setCampaignId(payload.getCampaignId());
            event.setSubscriberId(payload.getSubscriberId());
            event.setMessageId(payload.getMessageId());
            event.setExperimentId(payload.getExperimentId());
            event.setVariantId(payload.getVariantId());
            event.setHoldout(Boolean.TRUE.equals(payload.getHoldout()));
            event.setUserAgent(payload.getUserAgent());
            event.setIpAddress(payload.getIpAddress());
            event.setLinkUrl(payload.getLinkUrl());
            event.setTimestamp(payload.getTimestamp());
            if (payload.getMetadata() != null) {
                event.setMetadata(objectMapper.writeValueAsString(payload.getMetadata()));
            }
            rawEventRepository.save(event);
            log.debug("Saved event {} to database", payload.getId());
        } catch (Exception e) {
            log.error("Failed to save event to database: {}", payload.getId(), e);
            throw new IllegalStateException("Failed to persist tracking event " + payload.getId(), e);
        }
    }

    /**
     * Writes a durable outbox record in the same transaction as RawEvent. The
     * outbox service publishes after commit and retries if Kafka is unavailable.
     */
    private void publishEventWithOutbox(TrackingDto.RawEventPayload payload) {
        outboxService.enqueue(payload);
    }

    private TrackingDto.RawEventPayload buildPayload(String eventType, String tenantId, String workspaceId, String campaignId, String subscriberId,
                                                     String messageId, String experimentId, String variantId, boolean holdout,
                                                     String idempotencyKey, String linkUrl, String uaString, String ip, Map<String, Object> customMeta) {
        
        Map<String, Object> metadata = customMeta != null ? customMeta : new HashMap<>();
        
        if (uaString != null) {
            UserAgent ua = UserAgent.parseUserAgentString(uaString);
            metadata.put("browser", ua.getBrowser().getName());
            metadata.put("os", ua.getOperatingSystem().getName());
            metadata.put("deviceType", ua.getOperatingSystem().getDeviceType().getName());
        }

        return TrackingDto.RawEventPayload.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .teamId(null)
                .environmentId(TenantContext.getEnvironmentId())
                .actorId(TenantContext.getUserId())
                .ownershipScope("WORKSPACE")
                .idempotencyKey(idempotencyKey)
                .eventType(eventType)
                .campaignId(campaignId)
                .subscriberId(subscriberId)
                .messageId(messageId)
                .experimentId(experimentId)
                .variantId(variantId)
                .holdout(holdout)
                .userAgent(uaString)
                .ipAddress(ip)
                .linkUrl(linkUrl)
                .timestamp(Instant.now())
                .metadata(metadata)
                .build();
    }

    private String resolveWorkspaceId(String tenantId, String messageId, String workspaceId) {
        if (workspaceId != null && !workspaceId.isBlank()) {
            return workspaceId.trim();
        }
        return rawEventRepository.findTopByTenantIdAndMessageIdAndWorkspaceIdIsNotNullOrderByTimestampDesc(tenantId, messageId)
                .map(RawEvent::getWorkspaceId)
                .filter(existing -> existing != null && !existing.isBlank())
                .orElseGet(() -> {
                    log.warn("Missing workspace on tracking event. tenantId={}, messageId={}", tenantId, messageId);
                    throw new IllegalArgumentException("workspaceId is required for tracking event");
                });
    }
}
