package com.legent.campaign.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.legent.campaign.domain.CampaignExperiment;
import com.legent.campaign.domain.CampaignVariantMetric;
import com.legent.campaign.dto.CampaignEngineDto;
import com.legent.campaign.repository.CampaignVariantMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignMetricsService {

    private final CampaignVariantMetricRepository metricRepository;

    @Transactional
    public void recordTarget(String tenantId,
                             String workspaceId,
                             String campaignId,
                             String experimentId,
                             String variantId,
                             boolean holdout) {
        if (experimentId == null || experimentId.isBlank()) {
            return;
        }
        CampaignVariantMetric metric = getOrCreate(tenantId, workspaceId, campaignId, experimentId, variantId);
        if (holdout) {
            metric.setHoldoutCount(increment(metric.getHoldoutCount()));
        } else {
            metric.setTargetCount(increment(metric.getTargetCount()));
        }
        metricRepository.save(metric);
    }

    @Transactional
    public void recordDelivery(String tenantId,
                               String workspaceId,
                               String campaignId,
                               String experimentId,
                               String variantId,
                               boolean failed) {
        if (experimentId == null || experimentId.isBlank()) {
            return;
        }
        CampaignVariantMetric metric = getOrCreate(tenantId, workspaceId, campaignId, experimentId, variantId);
        if (failed) {
            metric.setFailedCount(increment(metric.getFailedCount()));
        } else {
            metric.setSentCount(increment(metric.getSentCount()));
        }
        metricRepository.save(metric);
    }

    @Transactional
    public void recordTracking(String tenantId,
                               String workspaceId,
                               String campaignId,
                               String experimentId,
                               String variantId,
                               String eventType,
                               Map<String, Object> metadata) {
        if (experimentId == null || experimentId.isBlank() || eventType == null || eventType.isBlank()) {
            return;
        }
        CampaignVariantMetric metric = getOrCreate(tenantId, workspaceId, campaignId, experimentId, variantId);
        switch (eventType.trim().toUpperCase(Locale.ROOT)) {
            case "OPEN" -> metric.setOpenCount(increment(metric.getOpenCount()));
            case "CLICK" -> metric.setClickCount(increment(metric.getClickCount()));
            case "CONVERSION" -> {
                metric.setConversionCount(increment(metric.getConversionCount()));
                metric.setRevenue(metric.getRevenue().add(parseMoney(metadata != null ? metadata.get("value") : null)));
            }
            case "CUSTOM" -> metric.setCustomMetricCount(increment(metric.getCustomMetricCount()));
            default -> {
                if (metadata != null && metadata.containsKey("customMetricName")) {
                    metric.setCustomMetricCount(increment(metric.getCustomMetricCount()));
                }
            }
        }
        metricRepository.save(metric);
    }

    @Transactional(readOnly = true)
    public List<CampaignEngineDto.VariantMetricsResponse> getMetrics(String tenantId,
                                                                     String workspaceId,
                                                                     String campaignId,
                                                                     CampaignExperiment experiment) {
        return metricRepository
                .findByTenantIdAndWorkspaceIdAndCampaignIdAndExperimentIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        tenantId, workspaceId, campaignId, experiment.getId())
                .stream()
                .map(metric -> toResponse(metric, experiment))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CampaignEngineDto.VariantMetricsResponse> chooseWinner(String tenantId,
                                                                          String workspaceId,
                                                                          String campaignId,
                                                                          CampaignExperiment experiment) {
        return getMetrics(tenantId, workspaceId, campaignId, experiment).stream()
                .filter(metric -> metric.getVariantId() != null)
                .filter(metric -> metric.getTargetCount() >= safe(experiment.getMinRecipientsPerVariant(), 100))
                .max(Comparator.comparing(CampaignEngineDto.VariantMetricsResponse::getScore));
    }

    @Transactional(readOnly = true)
    public CampaignEngineDto.ExperimentAnalysisResponse analyzeExperiment(String tenantId,
                                                                         String workspaceId,
                                                                         String campaignId,
                                                                         CampaignExperiment experiment) {
        List<CampaignEngineDto.VariantMetricsResponse> metrics = getMetrics(tenantId, workspaceId, campaignId, experiment);
        return analyzeMetrics(experiment, metrics);
    }

    CampaignEngineDto.ExperimentAnalysisResponse analyzeMetrics(CampaignExperiment experiment,
                                                                List<CampaignEngineDto.VariantMetricsResponse> metrics) {
        List<CampaignEngineDto.VariantMetricsResponse> eligible = metrics.stream()
                .filter(metric -> metric.getVariantId() != null)
                .toList();
        List<CampaignEngineDto.VariantMetricsResponse> ranked = eligible.stream()
                .sorted(Comparator.comparing(CampaignEngineDto.VariantMetricsResponse::getScore).reversed())
                .toList();
        CampaignEngineDto.VariantMetricsResponse winner = ranked.isEmpty() ? null : ranked.get(0);
        CampaignEngineDto.VariantMetricsResponse baseline = ranked.size() > 1 ? ranked.get(1) : winner;
        List<String> guardrails = experimentGuardrails(experiment, eligible, winner);
        BigDecimal confidence = winner == null || baseline == null || winner == baseline
                ? BigDecimal.ZERO
                : confidencePercent(winner, baseline, experiment);
        boolean autoAllowed = winner != null
                && confidence.compareTo(BigDecimal.valueOf(95)) >= 0
                && guardrails.isEmpty();

        BigDecimal observedLift = winner == null || baseline == null || winner == baseline
                ? BigDecimal.ZERO
                : liftPercent(metricRate(winner, experiment), metricRate(baseline, experiment));
        Map<String, Object> causalReport = new LinkedHashMap<>();
        causalReport.put("baselineVariantId", baseline == null ? null : baseline.getVariantId());
        causalReport.put("winnerVariantId", winner == null ? null : winner.getVariantId());
        causalReport.put("confidencePercent", confidence);
        causalReport.put("observedLiftPercent", observedLift);
        causalReport.put("holdoutCount", metrics.stream().mapToLong(CampaignEngineDto.VariantMetricsResponse::getHoldoutCount).sum());
        causalReport.put("method", "two_proportion_z_test_for_rate_metrics_ratio_guardrail_for_value_metrics");
        causalReport.put("notes", autoAllowed
                ? List.of("Winner meets confidence and sample guardrails.")
                : guardrails);

        return CampaignEngineDto.ExperimentAnalysisResponse.builder()
                .campaignId(experiment.getCampaignId())
                .experimentId(experiment.getId())
                .winnerMetric(experiment.getWinnerMetric())
                .recommendedWinnerVariantId(winner == null ? null : winner.getVariantId())
                .confidencePercent(confidence)
                .autoWinnerAllowed(autoAllowed)
                .guardrails(guardrails)
                .causalReport(causalReport)
                .variants(eligible.stream()
                        .map(metric -> variantAnalysis(metric, baseline, experiment))
                        .toList())
                .build();
    }

    private CampaignVariantMetric getOrCreate(String tenantId,
                                              String workspaceId,
                                              String campaignId,
                                              String experimentId,
                                              String variantId) {
        return metricRepository
                .findByTenantIdAndWorkspaceIdAndCampaignIdAndExperimentIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        tenantId, workspaceId, campaignId, experimentId)
                .stream()
                .filter(metric -> same(metric.getVariantId(), variantId))
                .findFirst()
                .orElseGet(() -> {
                    CampaignVariantMetric metric = new CampaignVariantMetric();
                    metric.setTenantId(tenantId);
                    metric.setWorkspaceId(workspaceId);
                    metric.setCampaignId(campaignId);
                    metric.setExperimentId(experimentId);
                    metric.setVariantId(variantId);
                    return metric;
                });
    }

    private CampaignEngineDto.VariantMetricsResponse toResponse(CampaignVariantMetric metric, CampaignExperiment experiment) {
        return CampaignEngineDto.VariantMetricsResponse.builder()
                .campaignId(metric.getCampaignId())
                .experimentId(metric.getExperimentId())
                .variantId(metric.getVariantId())
                .targetCount(safe(metric.getTargetCount()))
                .holdoutCount(safe(metric.getHoldoutCount()))
                .sentCount(safe(metric.getSentCount()))
                .failedCount(safe(metric.getFailedCount()))
                .openCount(safe(metric.getOpenCount()))
                .clickCount(safe(metric.getClickCount()))
                .conversionCount(safe(metric.getConversionCount()))
                .revenue(metric.getRevenue() == null ? BigDecimal.ZERO : metric.getRevenue())
                .customMetricCount(safe(metric.getCustomMetricCount()))
                .score(score(metric, experiment))
                .build();
    }

    private BigDecimal score(CampaignVariantMetric metric, CampaignExperiment experiment) {
        BigDecimal denominator = BigDecimal.valueOf(Math.max(1L, safe(metric.getSentCount())));
        return switch (experiment.getWinnerMetric()) {
            case OPENS -> BigDecimal.valueOf(safe(metric.getOpenCount())).divide(denominator, 6, RoundingMode.HALF_UP);
            case CLICKS -> BigDecimal.valueOf(safe(metric.getClickCount())).divide(denominator, 6, RoundingMode.HALF_UP);
            case CONVERSIONS -> BigDecimal.valueOf(safe(metric.getConversionCount())).divide(denominator, 6, RoundingMode.HALF_UP);
            case REVENUE -> metric.getRevenue() == null ? BigDecimal.ZERO : metric.getRevenue();
            case CUSTOM -> BigDecimal.valueOf(safe(metric.getCustomMetricCount()));
        };
    }

    private CampaignEngineDto.VariantAnalysisResponse variantAnalysis(CampaignEngineDto.VariantMetricsResponse metric,
                                                                      CampaignEngineDto.VariantMetricsResponse baseline,
                                                                      CampaignExperiment experiment) {
        BigDecimal rate = metricRate(metric, experiment);
        BigDecimal baselineRate = baseline == null ? BigDecimal.ZERO : metricRate(baseline, experiment);
        BigDecimal confidence = baseline == null || same(metric.getVariantId(), baseline.getVariantId())
                ? BigDecimal.ZERO
                : confidencePercent(metric, baseline, experiment);
        return CampaignEngineDto.VariantAnalysisResponse.builder()
                .variantId(metric.getVariantId())
                .samples(Math.max(metric.getSentCount(), metric.getTargetCount()))
                .metricRate(rate)
                .liftVsBaselinePercent(liftPercent(rate, baselineRate))
                .confidencePercent(confidence)
                .guardrailStatus(metricGuardrailStatus(metric, experiment))
                .build();
    }

    private List<String> experimentGuardrails(CampaignExperiment experiment,
                                              List<CampaignEngineDto.VariantMetricsResponse> metrics,
                                              CampaignEngineDto.VariantMetricsResponse winner) {
        List<String> guardrails = new ArrayList<>();
        if (metrics.size() < 2) {
            guardrails.add("At least two variants are required for confidence analysis.");
        }
        int minSamples = safe(experiment.getMinRecipientsPerVariant(), 100);
        if (winner == null || Math.max(winner.getSentCount(), winner.getTargetCount()) < minSamples) {
            guardrails.add("Recommended winner has fewer than " + minSamples + " samples.");
        }
        boolean highFailure = metrics.stream().anyMatch(metric -> metric.getSentCount() > 0
                && BigDecimal.valueOf(metric.getFailedCount())
                        .divide(BigDecimal.valueOf(metric.getSentCount()), 6, RoundingMode.HALF_UP)
                        .compareTo(BigDecimal.valueOf(0.05)) > 0);
        if (highFailure) {
            guardrails.add("One or more variants exceed 5% delivery failure rate.");
        }
        return guardrails;
    }

    private String metricGuardrailStatus(CampaignEngineDto.VariantMetricsResponse metric, CampaignExperiment experiment) {
        int minSamples = safe(experiment.getMinRecipientsPerVariant(), 100);
        if (Math.max(metric.getSentCount(), metric.getTargetCount()) < minSamples) {
            return "LOW_SAMPLE";
        }
        if (metric.getSentCount() > 0
                && BigDecimal.valueOf(metric.getFailedCount())
                .divide(BigDecimal.valueOf(metric.getSentCount()), 6, RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(0.05)) > 0) {
            return "HIGH_FAILURE_RATE";
        }
        return "PASS";
    }

    private BigDecimal metricRate(CampaignEngineDto.VariantMetricsResponse metric, CampaignExperiment experiment) {
        BigDecimal denominator = BigDecimal.valueOf(Math.max(1L, metric.getSentCount()));
        return switch (experiment.getWinnerMetric()) {
            case OPENS -> BigDecimal.valueOf(metric.getOpenCount()).divide(denominator, 6, RoundingMode.HALF_UP);
            case CLICKS -> BigDecimal.valueOf(metric.getClickCount()).divide(denominator, 6, RoundingMode.HALF_UP);
            case CONVERSIONS -> BigDecimal.valueOf(metric.getConversionCount()).divide(denominator, 6, RoundingMode.HALF_UP);
            case REVENUE -> metric.getRevenue() == null ? BigDecimal.ZERO : metric.getRevenue().divide(denominator, 6, RoundingMode.HALF_UP);
            case CUSTOM -> BigDecimal.valueOf(metric.getCustomMetricCount()).divide(denominator, 6, RoundingMode.HALF_UP);
        };
    }

    private BigDecimal confidencePercent(CampaignEngineDto.VariantMetricsResponse candidate,
                                         CampaignEngineDto.VariantMetricsResponse baseline,
                                         CampaignExperiment experiment) {
        if (experiment.getWinnerMetric() == CampaignExperiment.WinnerMetric.REVENUE
                || experiment.getWinnerMetric() == CampaignExperiment.WinnerMetric.CUSTOM) {
            BigDecimal lift = liftPercent(metricRate(candidate, experiment), metricRate(baseline, experiment)).abs();
            long samples = Math.min(Math.max(candidate.getSentCount(), candidate.getTargetCount()),
                    Math.max(baseline.getSentCount(), baseline.getTargetCount()));
            BigDecimal sampleConfidence = BigDecimal.valueOf(Math.min(35, samples / 50.0));
            return lift.min(BigDecimal.valueOf(60)).add(sampleConfidence).min(BigDecimal.valueOf(99)).setScale(2, RoundingMode.HALF_UP);
        }

        long n1 = Math.max(1L, candidate.getSentCount());
        long n2 = Math.max(1L, baseline.getSentCount());
        double p1 = metricRate(candidate, experiment).doubleValue();
        double p2 = metricRate(baseline, experiment).doubleValue();
        double pooled = ((p1 * n1) + (p2 * n2)) / (n1 + n2);
        double se = Math.sqrt(Math.max(0.000001, pooled * (1 - pooled) * ((1.0 / n1) + (1.0 / n2))));
        double z = Math.abs((p1 - p2) / se);
        double confidence = normalCdf(z) * 100.0;
        return BigDecimal.valueOf(Math.min(99.9, confidence)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal liftPercent(BigDecimal value, BigDecimal baseline) {
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.subtract(baseline)
                .divide(baseline, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private double normalCdf(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * z);
        double density = 0.3989423 * Math.exp(-z * z / 2.0);
        double probability = 1 - density * (((((1.330274429 * t - 1.821255978) * t) + 1.781477937) * t - 0.356563782) * t + 0.319381530) * t;
        return Math.max(0.5, Math.min(0.999, probability));
    }

    private BigDecimal parseMoney(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private long increment(Long value) {
        return safe(value) + 1L;
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    private int safe(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
