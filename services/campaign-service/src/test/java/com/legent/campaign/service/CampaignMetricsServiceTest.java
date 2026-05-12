package com.legent.campaign.service;

import com.legent.campaign.domain.CampaignExperiment;
import com.legent.campaign.dto.CampaignEngineDto;
import com.legent.campaign.repository.CampaignVariantMetricRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CampaignMetricsServiceTest {

    @Test
    void analyzeMetrics_returnsConfidenceAndAutoWinnerWhenGuardrailsPass() {
        CampaignMetricsService service = new CampaignMetricsService(mock(CampaignVariantMetricRepository.class));
        CampaignExperiment experiment = new CampaignExperiment();
        experiment.setId("experiment-1");
        experiment.setCampaignId("campaign-1");
        experiment.setWinnerMetric(CampaignExperiment.WinnerMetric.CLICKS);
        experiment.setMinRecipientsPerVariant(500);

        CampaignEngineDto.VariantMetricsResponse winner = CampaignEngineDto.VariantMetricsResponse.builder()
                .campaignId("campaign-1")
                .experimentId("experiment-1")
                .variantId("variant-a")
                .targetCount(1000)
                .sentCount(1000)
                .clickCount(200)
                .score(BigDecimal.valueOf(0.20))
                .build();
        CampaignEngineDto.VariantMetricsResponse baseline = CampaignEngineDto.VariantMetricsResponse.builder()
                .campaignId("campaign-1")
                .experimentId("experiment-1")
                .variantId("variant-b")
                .targetCount(1000)
                .sentCount(1000)
                .clickCount(100)
                .score(BigDecimal.valueOf(0.10))
                .build();

        CampaignEngineDto.ExperimentAnalysisResponse analysis = service.analyzeMetrics(experiment, List.of(winner, baseline));

        assertThat(analysis.getRecommendedWinnerVariantId()).isEqualTo("variant-a");
        assertThat(analysis.getConfidencePercent()).isGreaterThanOrEqualTo(BigDecimal.valueOf(95));
        assertThat(analysis.isAutoWinnerAllowed()).isTrue();
        assertThat(analysis.getGuardrails()).isEmpty();
        assertThat(analysis.getCausalReport()).containsEntry("winnerVariantId", "variant-a");
    }
}
