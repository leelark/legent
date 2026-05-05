package com.legent.audience.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.SubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriberIntelligenceService {

    private final SubscriberRepository subscriberRepository;
    private final AudienceEventIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void applyTrackingIngested(String tenantId, String workspaceId, String eventType, String eventId, String idempotencyKey, String payloadJson) {
        if (!idempotencyService.registerIfNew(tenantId, workspaceId, eventType, eventId, idempotencyKey)) {
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {});
            String subscriberId = payload.get("subscriberId") == null ? null : String.valueOf(payload.get("subscriberId"));
            String trackingType = payload.get("eventType") == null ? null : String.valueOf(payload.get("eventType")).toUpperCase(Locale.ROOT);
            if (subscriberId == null || trackingType == null) return;

            subscriberRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, subscriberId).ifPresent(subscriber -> {
                subscriber.setLastActivityAt(Instant.now());
                switch (trackingType) {
                    case "OPEN" -> subscriber.setOpenScore(subscriber.getOpenScore() + 1);
                    case "CLICK" -> subscriber.setClickScore(subscriber.getClickScore() + 3);
                    case "CONVERSION" -> subscriber.setConversionScore(subscriber.getConversionScore() + 5);
                    default -> {
                    }
                }
                recalcScores(subscriber);
                if (subscriber.getTotalScore() >= 25 && !"ENGAGED".equalsIgnoreCase(subscriber.getLifecycleStage())) {
                    subscriber.setLifecycleStage("ENGAGED");
                    subscriber.setLifecycleStageAt(Instant.now());
                }
                appendTimeline(subscriber, "TRACKING_" + trackingType, payload);
                subscriberRepository.save(subscriber);
            });
        } catch (Exception e) {
            log.error("Failed applying tracking intelligence event", e);
        }
    }

    @Transactional
    public void applyAutomationEvent(String tenantId, String workspaceId, String eventType, String eventId, String idempotencyKey, String payloadJson) {
        if (!idempotencyService.registerIfNew(tenantId, workspaceId, eventType, eventId, idempotencyKey)) {
            return;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {});
            String subscriberId = payload.get("subscriberId") == null ? null : String.valueOf(payload.get("subscriberId"));
            if (subscriberId == null) return;
            subscriberRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, subscriberId).ifPresent(subscriber -> {
                subscriber.setActivityScore(subscriber.getActivityScore() + 2);
                subscriber.setLastActivityAt(Instant.now());
                recalcScores(subscriber);
                appendTimeline(subscriber, eventType, payload);
                subscriberRepository.save(subscriber);
            });
        } catch (Exception e) {
            log.error("Failed applying automation intelligence event", e);
        }
    }

    private void recalcScores(Subscriber subscriber) {
        subscriber.setEngagementScore(subscriber.getOpenScore() + subscriber.getClickScore() + subscriber.getConversionScore());
        subscriber.setTotalScore(
                subscriber.getOpenScore()
                        + subscriber.getClickScore()
                        + subscriber.getConversionScore()
                        + subscriber.getRecencyScore()
                        + subscriber.getFrequencyScore()
                        + subscriber.getEngagementScore()
                        + subscriber.getActivityScore());
    }

    private void appendTimeline(Subscriber subscriber, String type, Map<String, Object> metadata) {
        List<Map<String, Object>> timeline = subscriber.getTimeline();
        if (timeline == null) timeline = new ArrayList<>();
        timeline.add(Map.of(
                "type", type,
                "at", Instant.now().toString(),
                "metadata", metadata
        ));
        if (timeline.size() > 200) {
            timeline = new ArrayList<>(timeline.subList(timeline.size() - 200, timeline.size()));
        }
        subscriber.setTimeline(timeline);
    }
}
