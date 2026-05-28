package com.legent.campaign.service;

import com.legent.campaign.domain.CampaignDeadLetter;
import com.legent.campaign.repository.CampaignDeadLetterRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignDeadLetterMetricsServiceTest {

    @Mock private CampaignDeadLetterRepository deadLetterRepository;

    @Test
    void exposesOpenDepthAgeAndJobSkewWithOnlySourceLabel() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Instant now = Instant.parse("2026-05-24T09:30:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        when(deadLetterRepository.countByStatusAndDeletedAtIsNull(CampaignDeadLetter.STATUS_OPEN)).thenReturn(10L);
        when(deadLetterRepository.findOldestCreatedAtByStatus(CampaignDeadLetter.STATUS_OPEN))
                .thenReturn(Optional.of(now.minusSeconds(600)));
        when(deadLetterRepository.findOpenCountsByJob(CampaignDeadLetter.STATUS_OPEN, PageRequest.of(0, 1)))
                .thenReturn(List.of(7L));

        CampaignDeadLetterMetricsService service =
                new CampaignDeadLetterMetricsService(registry, deadLetterRepository, clock);
        assertNotNull(service);

        assertEquals(10.0, registry.get("legent.dlq.depth")
                .tag("source", "campaign_dead_letters")
                .gauge()
                .value());
        assertEquals(600.0, registry.get("legent.dlq.oldest.age.seconds")
                .tag("source", "campaign_dead_letters")
                .gauge()
                .value());
        assertEquals(0.7, registry.get("legent.dlq.skew.ratio")
                .tag("source", "campaign_dead_letters")
                .gauge()
                .value(), 0.0001);

        registry.forEachMeter(meter -> assertEquals(1, meter.getId().getTags().size()));
        verify(deadLetterRepository).findOpenCountsByJob(CampaignDeadLetter.STATUS_OPEN, PageRequest.of(0, 1));
    }
}
