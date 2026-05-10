package com.legent.campaign.service;

import java.math.BigDecimal;
import java.util.Comparator;
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
            case OPENS -> BigDecimal.valueOf(safe(metric.getOpenCount())).divide(denominator, 6, java.math.RoundingMode.HALF_UP);
            case CLICKS -> BigDecimal.valueOf(safe(metric.getClickCount())).divide(denominator, 6, java.math.RoundingMode.HALF_UP);
            case CONVERSIONS -> BigDecimal.valueOf(safe(metric.getConversionCount())).divide(denominator, 6, java.math.RoundingMode.HALF_UP);
            case REVENUE -> metric.getRevenue() == null ? BigDecimal.ZERO : metric.getRevenue();
            case CUSTOM -> BigDecimal.valueOf(safe(metric.getCustomMetricCount()));
        };
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
