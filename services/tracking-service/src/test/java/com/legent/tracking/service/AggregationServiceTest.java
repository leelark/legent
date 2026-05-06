package com.legent.tracking.service;

import java.util.Optional;


import java.time.Instant;

import com.legent.tracking.domain.CampaignSummary;
import com.legent.tracking.domain.RawEvent;
import com.legent.tracking.domain.SubscriberSummary;
import com.legent.tracking.repository.CampaignSummaryRepository;
import com.legent.tracking.repository.SubscriberSummaryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)

class AggregationServiceTest {

    @Mock private CampaignSummaryRepository campaignSummaryRepository;
    @Mock private SubscriberSummaryRepository subscriberSummaryRepository;
    @Mock private com.legent.cache.service.CacheService cacheService;

    @InjectMocks private AggregationService aggregationService;

    @Test
    void aggregateEvent_IncrementsCampaignOpen() {
        RawEvent event = new RawEvent();
        event.setTenantId("t1");
        event.setWorkspaceId("workspace-default");
        event.setCampaignId("c1");
        event.setSubscriberId("s1");
        event.setEventType("OPEN");
        event.setTimestamp(Instant.now());

        CampaignSummary existingSummary = new CampaignSummary();
        existingSummary.setTotalOpens(5L);

        when(campaignSummaryRepository.findByTenantIdAndWorkspaceIdAndCampaignId("t1", "workspace-default", "c1"))
                .thenReturn(Optional.of(existingSummary));
        when(subscriberSummaryRepository.findByTenantIdAndWorkspaceIdAndSubscriberId("t1", "workspace-default", "s1"))
                .thenReturn(Optional.empty());
        when(cacheService.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(Optional.empty());

        aggregationService.aggregateEvent(event);

        assertEquals(6L, existingSummary.getTotalOpens());
        verify(campaignSummaryRepository).save(existingSummary);
        verify(subscriberSummaryRepository).save(any(SubscriberSummary.class));
    }
}
