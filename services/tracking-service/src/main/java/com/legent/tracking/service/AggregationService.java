package com.legent.tracking.service;

import com.legent.tracking.domain.CampaignSummary;
import com.legent.tracking.domain.RawEvent;
import com.legent.tracking.domain.SubscriberSummary;
import com.legent.tracking.repository.CampaignSummaryRepository;
import com.legent.tracking.repository.SubscriberSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregationService {

    private final CampaignSummaryRepository campaignSummaryRepository;
    private final SubscriberSummaryRepository subscriberSummaryRepository;
    private final com.legent.cache.service.CacheService cacheService;

    @Transactional
    public void aggregateEvent(RawEvent event) {
        if (event == null || event.getTenantId() == null || event.getTenantId().isBlank()
                || event.getWorkspaceId() == null || event.getWorkspaceId().isBlank()
                || event.getEventType() == null || event.getEventType().isBlank()) {
            log.warn("Skipping invalid tracking event aggregation");
            return;
        }
        if (event.getCampaignId() != null && !event.getCampaignId().isBlank()) {
            aggregateCampaign(event);
        }
        if (event.getSubscriberId() != null && !event.getSubscriberId().isBlank()) {
            aggregateSubscriber(event);
        }
    }

    private void aggregateCampaign(RawEvent event) {
        CampaignSummary summary = campaignSummaryRepository.findByTenantIdAndWorkspaceIdAndCampaignId(
                        event.getTenantId(), event.getWorkspaceId(), event.getCampaignId())
                .orElse(new CampaignSummary());
        if (summary.getId() == null) {
            summary.setTenantId(event.getTenantId());
            summary.setWorkspaceId(event.getWorkspaceId());
            summary.setTeamId(event.getTeamId());
            summary.setOwnershipScope(event.getOwnershipScope() == null ? "WORKSPACE" : event.getOwnershipScope());
            summary.setCampaignId(event.getCampaignId());
        }

        switch (event.getEventType()) {
            case "OPEN":
                summary.setTotalOpens(increment(summary.getTotalOpens()));
                if (hasSubscriber(event) && isUniqueAction(event.getTenantId(), "uniq_open_camp", event.getCampaignId(), event.getSubscriberId())) {
                    summary.setUniqueOpens(increment(summary.getUniqueOpens()));
                }
                break;
            case "CLICK":
                summary.setTotalClicks(increment(summary.getTotalClicks()));
                if (hasSubscriber(event) && isUniqueAction(event.getTenantId(), "uniq_clk_camp", event.getCampaignId(), event.getSubscriberId())) {
                    summary.setUniqueClicks(increment(summary.getUniqueClicks()));
                }
                break;
            case "CONVERSION":
                summary.setTotalConversions(increment(summary.getTotalConversions()));
                break;
            default:
                log.debug("Ignoring unsupported campaign event type {}", event.getEventType());
        }
        campaignSummaryRepository.save(summary);
    }

    private void aggregateSubscriber(RawEvent event) {
        SubscriberSummary summary = subscriberSummaryRepository.findByTenantIdAndWorkspaceIdAndSubscriberId(
                        event.getTenantId(), event.getWorkspaceId(), event.getSubscriberId())
                .orElse(new SubscriberSummary());
        if (summary.getId() == null) {
            summary.setTenantId(event.getTenantId());
            summary.setWorkspaceId(event.getWorkspaceId());
            summary.setTeamId(event.getTeamId());
            summary.setOwnershipScope(event.getOwnershipScope() == null ? "WORKSPACE" : event.getOwnershipScope());
            summary.setSubscriberId(event.getSubscriberId());
        }

        summary.setLastEngagedAt(event.getTimestamp());

        switch (event.getEventType()) {
            case "OPEN":
                summary.setTotalOpens(increment(summary.getTotalOpens()));
                break;
            case "CLICK":
                summary.setTotalClicks(increment(summary.getTotalClicks()));
                break;
            default:
                log.debug("Ignoring unsupported subscriber event type {}", event.getEventType());
        }
        subscriberSummaryRepository.save(summary);
    }

    private boolean isUniqueAction(String tenantId, String metricPrefix, String entityId, String subId) {
        String key = "track:" + metricPrefix + ":" + tenantId + ":" + entityId + ":" + subId;
        if (cacheService.get(key, String.class).isPresent()) {
            return false;
        }
        cacheService.set(key, "1", java.time.Duration.ofDays(30)); // 30 day unique window
        return true;
    }

    private boolean hasSubscriber(RawEvent event) {
        return event.getSubscriberId() != null && !event.getSubscriberId().isBlank();
    }

    private long increment(Long value) {
        return value == null ? 1L : value + 1;
    }
}
