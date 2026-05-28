package com.legent.campaign.service;

import com.legent.campaign.repository.CampaignDeadLetterRepository;
import com.legent.campaign.repository.SendBatchRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class CampaignMetricsServiceWiringTest {

    @Test
    void wiresMetricsServicesWithExplicitProductionConstructors() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "campaignMetricsTest",
                    Map.of("legent.campaign.send.processing-lease-timeout", "PT15M")));
            context.registerBean(SimpleMeterRegistry.class);
            context.registerBean(CampaignDeadLetterRepository.class, () -> mock(CampaignDeadLetterRepository.class));
            context.registerBean(SendBatchRepository.class, () -> mock(SendBatchRepository.class));
            context.register(CampaignDeadLetterMetricsService.class, CampaignRetryMetricsService.class);

            context.refresh();

            assertNotNull(context.getBean(CampaignDeadLetterMetricsService.class));
            assertNotNull(context.getBean(CampaignRetryMetricsService.class));
        }
    }
}
