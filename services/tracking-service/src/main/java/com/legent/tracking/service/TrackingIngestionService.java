package com.legent.tracking.service;

import java.util.Map;

import java.time.Instant;

import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.event.TrackingEventPublisher;
import eu.bitwalker.useragentutils.UserAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingIngestionService {

    private final TrackingEventPublisher eventPublisher;
    private final com.legent.cache.service.CacheService cacheService;

    public void processOpen(String tenantId, String campaignId, String subscriberId, String messageId, String userAgentString, String ipAddress) {
        if (!isDuplicate(tenantId, "OPEN", messageId, subscriberId, "")) {
            TrackingDto.RawEventPayload payload = buildPayload("OPEN", tenantId, campaignId, subscriberId, messageId, null, userAgentString, ipAddress, null);
            eventPublisher.publishIngestedEvent(payload);
        }
    }

    public void processClick(String tenantId, String campaignId, String subscriberId, String messageId, String linkUrl, String userAgentString, String ipAddress) {
        if (!isDuplicate(tenantId, "CLICK", messageId, subscriberId, linkUrl)) {
            TrackingDto.RawEventPayload payload = buildPayload("CLICK", tenantId, campaignId, subscriberId, messageId, linkUrl, userAgentString, ipAddress, null);
            eventPublisher.publishIngestedEvent(payload);
        }
    }

    public void processConversion(String tenantId, TrackingDto.ConversionRequest request, String userAgentString, String ipAddress) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("eventName", request.getEventName());
        meta.put("value", request.getValue());
        meta.put("currency", request.getCurrency());

        TrackingDto.RawEventPayload payload = buildPayload("CONVERSION", tenantId, request.getCampaignId(), request.getSubscriberId(), null, null, userAgentString, ipAddress, meta);
        eventPublisher.publishIngestedEvent(payload);
    }

    private boolean isDuplicate(String tenantId, String type, String msgId, String subId, String url) {
        String key = "track:dedup:" + tenantId + ":" + type + ":" + msgId + ":" + subId + (url != null ? ":" + url.hashCode() : "");
        if (cacheService.get(key, String.class).isPresent()) {
            return true;
        }
        cacheService.set(key, "1", java.time.Duration.ofHours(2)); // High frequency deduplication window
        return false;
    }

    private TrackingDto.RawEventPayload buildPayload(String eventType, String tenantId, String campaignId, String subscriberId, 
                                                     String messageId, String linkUrl, String uaString, String ip, Map<String, Object> customMeta) {
        
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
                .eventType(eventType)
                .campaignId(campaignId)
                .subscriberId(subscriberId)
                .messageId(messageId)
                .userAgent(uaString)
                .ipAddress(ip)
                .linkUrl(linkUrl)
                .timestamp(Instant.now())
                .metadata(metadata)
                .build();
    }
}
