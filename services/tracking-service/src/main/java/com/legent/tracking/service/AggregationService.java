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
        if (event.getCampaignId() != null) {
            aggregateCampaign(event);
        }
        if (event.getSubscriberId() != null) {
            aggregateSubscriber(event);
        }
    }

    private void aggregateCampaign(RawEvent event) {
        CampaignSummary summary = campaignSummaryRepository.findByTenantIdAndCampaignId(event.getTenantId(), event.getCampaignId())
                .orElse(new CampaignSummary());
        if (summary.getId() == null) {
            summary.setTenantId(event.getTenantId());
            summary.setCampaignId(event.getCampaignId());
        }

        switch (event.getEventType()) {
            case "OPEN":
                summary.setTotalOpens(summary.getTotalOpens() + 1);
                if (isUniqueAction(event.getTenantId(), "uniq_open_camp", event.getCampaignId(), event.getSubscriberId())) {
                    summary.setUniqueOpens(summary.getUniqueOpens() + 1);
                }
                break;
            case "CLICK":
                summary.setTotalClicks(summary.getTotalClicks() + 1);
                if (isUniqueAction(event.getTenantId(), "uniq_clk_camp", event.getCampaignId(), event.getSubscriberId())) {
                    summary.setUniqueClicks(summary.getUniqueClicks() + 1);
                }
                break;
            case "CONVERSION":
                summary.setTotalConversions(summary.getTotalConversions() + 1);
                break;
        }
        campaignSummaryRepository.save(summary);
    }

    private void aggregateSubscriber(RawEvent event) {
        SubscriberSummary summary = subscriberSummaryRepository.findByTenantIdAndSubscriberId(event.getTenantId(), event.getSubscriberId())
                .orElse(new SubscriberSummary());
        if (summary.getId() == null) {
            summary.setTenantId(event.getTenantId());
            summary.setSubscriberId(event.getSubscriberId());
        }

        summary.setLastEngagedAt(event.getTimestamp());

        switch (event.getEventType()) {
            case "OPEN":
                summary.setTotalOpens(summary.getTotalOpens() + 1);
                break;
            case "CLICK":
                summary.setTotalClicks(summary.getTotalClicks() + 1);
                break;
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
}
