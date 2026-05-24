package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.dto.CampaignDto;
import com.legent.common.exception.ValidationException;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class CampaignSendTimeOptimizationGuard {

    private static final String OPTIMIZATION_TYPE = "SEND_TIME";
    private static final String FALLBACK_NONE = "NONE";
    private static final List<String> LAUNCH_CONFIDENCE_BANDS = List.of("HIGH", "MEDIUM");

    private CampaignSendTimeOptimizationGuard() {
    }

    static Instant resolveRequiredSchedule(Campaign campaign,
                                           Instant requestedScheduledAt,
                                           CampaignDto.SendTimeOptimizationDecision decision) {
        Instant effective = resolveOptionalSchedule(campaign, requestedScheduledAt, decision);
        if (effective == null) {
            throw new ValidationException("scheduledAt", "scheduledAt is required");
        }
        return effective;
    }

    static Instant resolveOptionalSchedule(Campaign campaign,
                                           Instant requestedScheduledAt,
                                           CampaignDto.SendTimeOptimizationDecision decision) {
        if (decision != null) {
            Instant effective = validateDecision(campaign, requestedScheduledAt, decision, true);
            applyDecision(campaign, decision, effective);
            return effective;
        }
        if (hasStoredDecision(campaign)) {
            return validateDecision(campaign, requestedScheduledAt, fromCampaign(campaign), true);
        }
        return requestedScheduledAt;
    }

    static Instant previewOptionalSchedule(Campaign campaign,
                                           Instant requestedScheduledAt,
                                           CampaignDto.SendTimeOptimizationDecision decision) {
        if (decision != null) {
            return validateDecision(campaign, requestedScheduledAt, decision, true);
        }
        if (hasStoredDecision(campaign)) {
            return validateDecision(campaign, requestedScheduledAt, fromCampaign(campaign), true);
        }
        return requestedScheduledAt;
    }

    static void validatePendingJobDispatch(Campaign campaign, SendJob job) {
        if (!hasStoredDecision(campaign)) {
            return;
        }
        validateDecision(campaign, job == null ? null : job.getScheduledAt(), fromCampaign(campaign), false);
    }

    private static Instant validateDecision(Campaign campaign,
                                            Instant requestedScheduledAt,
                                            CampaignDto.SendTimeOptimizationDecision decision,
                                            boolean requireFuture) {
        requireType(decision);
        requireText(decision.getPolicyKey(), "sendTimeOptimization.policyKey",
                "Approved send-time optimization requires policy evidence");
        requireText(decision.getOptimizationRunId(), "sendTimeOptimization.optimizationRunId",
                "Approved send-time optimization requires run evidence");
        requireText(decision.getSnapshotHash(), "sendTimeOptimization.snapshotHash",
                "Approved send-time optimization requires snapshot hash evidence");
        if (decision.getOriginalScheduledAt() == null) {
            throw new ValidationException("sendTimeOptimization.originalScheduledAt",
                    "Approved send-time optimization requires original schedule evidence");
        }
        Instant recommendedAt = decision.getRecommendedScheduledAt();
        if (recommendedAt == null) {
            throw new ValidationException("sendTimeOptimization.recommendedScheduledAt",
                    "Approved send-time optimization requires recommended schedule evidence");
        }
        if (requestedScheduledAt != null && !recommendedAt.equals(requestedScheduledAt)) {
            throw new ValidationException("scheduledAt",
                    "Scheduled time must match the approved send-time optimization decision");
        }
        if (requireFuture && !recommendedAt.isAfter(Instant.now())) {
            throw new ValidationException("sendTimeOptimization.recommendedScheduledAt",
                    "Approved send-time optimization schedule must be in the future");
        }

        requireReadyDecision(decision);
        validateLocalScheduleWindow(campaign, decision, recommendedAt);
        return recommendedAt;
    }

    private static void requireReadyDecision(CampaignDto.SendTimeOptimizationDecision decision) {
        String confidenceBand = normalize(decision.getConfidenceBand());
        if (!LAUNCH_CONFIDENCE_BANDS.contains(confidenceBand)) {
            throw new ValidationException("sendTimeOptimization.confidenceBand",
                    "Send-time optimization schedule changes require medium or high confidence");
        }
        if (!FALLBACK_NONE.equals(normalize(decision.getFallbackMode()))) {
            throw new ValidationException("sendTimeOptimization.fallbackMode",
                    "Send-time optimization fallback decisions cannot change launch timing");
        }
        if (!normalizeList(decision.getBlockedReasons()).isEmpty()) {
            throw new ValidationException("sendTimeOptimization.blockedReasons",
                    "Send-time optimization blocked reasons must be resolved before scheduling");
        }
        if (!Boolean.TRUE.equals(decision.getApprovalRequired())) {
            throw new ValidationException("sendTimeOptimization.approvalRequired",
                    "Send-time optimization launch changes require approval evidence");
        }
        if (!Boolean.TRUE.equals(decision.getRollbackRequired())) {
            throw new ValidationException("sendTimeOptimization.rollbackRequired",
                    "Send-time optimization launch changes require rollback evidence");
        }
        if (!Boolean.TRUE.equals(decision.getApproved())) {
            throw new ValidationException("sendTimeOptimization.approved",
                    "Send-time optimization decision must be approved before scheduling");
        }
        if (decision.getApprovedAt() == null) {
            throw new ValidationException("sendTimeOptimization.approvedAt",
                    "Approved send-time optimization requires approval timestamp evidence");
        }
        if (isBlank(decision.getApprovalId()) && isBlank(decision.getApprovedBy())) {
            throw new ValidationException("sendTimeOptimization.approvalId",
                    "Approved send-time optimization requires approval actor or approval id evidence");
        }
        requireText(decision.getRollbackSnapshotId(), "sendTimeOptimization.rollbackSnapshotId",
                "Approved send-time optimization requires rollback snapshot evidence");
        requireGate(decision.getQuietHoursGatePassed(), "quietHoursGatePassed");
        requireGate(decision.getApprovalGatePassed(), "approvalGatePassed");
        requireGate(decision.getSuppressionGatePassed(), "suppressionGatePassed");
        requireGate(decision.getWarmupGatePassed(), "warmupGatePassed");
        requireGate(decision.getRateLimitGatePassed(), "rateLimitGatePassed");
        requireGate(decision.getProviderCapacityGatePassed(), "providerCapacityGatePassed");
        requireGate(decision.getDeliverabilityGatePassed(), "deliverabilityGatePassed");
    }

    private static void requireType(CampaignDto.SendTimeOptimizationDecision decision) {
        String type = normalize(decision.getOptimizationType());
        if (!OPTIMIZATION_TYPE.equals(type)) {
            throw new ValidationException("sendTimeOptimization.optimizationType",
                    "Send-time optimization decisions must use optimizationType SEND_TIME");
        }
    }

    private static void requireGate(Boolean value, String field) {
        if (!Boolean.TRUE.equals(value)) {
            throw new ValidationException("sendTimeOptimization." + field,
                    "Send-time optimization gate must pass before scheduling: " + field);
        }
    }

    private static void validateLocalScheduleWindow(Campaign campaign,
                                                    CampaignDto.SendTimeOptimizationDecision decision,
                                                    Instant recommendedAt) {
        String decisionTimezone = requireText(decision.getTimezone(), "sendTimeOptimization.timezone",
                "Approved send-time optimization requires timezone evidence");
        String campaignTimezone = isBlank(campaign.getTimezone()) ? "UTC" : campaign.getTimezone().trim();
        if (!campaignTimezone.equals(decisionTimezone.trim())) {
            throw new ValidationException("sendTimeOptimization.timezone",
                    "Send-time optimization timezone must match campaign timezone");
        }
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(decisionTimezone.trim());
        } catch (Exception ex) {
            throw new ValidationException("sendTimeOptimization.timezone",
                    "Send-time optimization timezone is invalid");
        }
        LocalTime localLaunchTime = ZonedDateTime.ofInstant(recommendedAt, zoneId).toLocalTime();
        if (containsTime(campaign.getQuietHoursStart(), campaign.getQuietHoursEnd(), localLaunchTime)) {
            throw new ValidationException("sendTimeOptimization.recommendedScheduledAt",
                    "Send-time optimization schedule falls inside campaign quiet hours");
        }
        if (campaign.getSendWindowStart() != null && campaign.getSendWindowEnd() != null
                && !campaign.getSendWindowStart().equals(campaign.getSendWindowEnd())
                && !containsTime(campaign.getSendWindowStart(), campaign.getSendWindowEnd(), localLaunchTime)) {
            throw new ValidationException("sendTimeOptimization.recommendedScheduledAt",
                    "Send-time optimization schedule is outside the campaign send window");
        }
    }

    private static boolean containsTime(LocalTime start, LocalTime end, LocalTime time) {
        if (start == null || end == null || time == null || start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    private static void applyDecision(Campaign campaign,
                                      CampaignDto.SendTimeOptimizationDecision decision,
                                      Instant effectiveScheduledAt) {
        campaign.setSendTimeOptimizationPolicyKey(trimToNull(decision.getPolicyKey()));
        campaign.setSendTimeOptimizationType(OPTIMIZATION_TYPE);
        campaign.setSendTimeOptimizationRunId(trimToNull(decision.getOptimizationRunId()));
        campaign.setSendTimeOptimizationSnapshotHash(trimToNull(decision.getSnapshotHash()));
        campaign.setSendTimeOptimizationOriginalScheduledAt(decision.getOriginalScheduledAt());
        campaign.setSendTimeOptimizationRecommendedScheduledAt(effectiveScheduledAt);
        campaign.setSendTimeOptimizationTimezone(trimToNull(decision.getTimezone()));
        campaign.setSendTimeOptimizationConfidenceBand(normalize(decision.getConfidenceBand()));
        campaign.setSendTimeOptimizationFallbackMode(normalize(decision.getFallbackMode()));
        campaign.setSendTimeOptimizationBlockedReasons(normalizeList(decision.getBlockedReasons()));
        campaign.setSendTimeOptimizationDataQualityReasons(normalizeList(decision.getDataQualityReasons()));
        campaign.setSendTimeOptimizationReasonCodes(normalizeList(decision.getReasonCodes()));
        campaign.setSendTimeOptimizationApprovalRequired(Boolean.TRUE.equals(decision.getApprovalRequired()));
        campaign.setSendTimeOptimizationRollbackRequired(Boolean.TRUE.equals(decision.getRollbackRequired()));
        campaign.setSendTimeOptimizationApproved(Boolean.TRUE.equals(decision.getApproved()));
        campaign.setSendTimeOptimizationApprovalId(trimToNull(decision.getApprovalId()));
        campaign.setSendTimeOptimizationApprovedBy(trimToNull(decision.getApprovedBy()));
        campaign.setSendTimeOptimizationApprovedAt(decision.getApprovedAt());
        campaign.setSendTimeOptimizationRollbackSnapshotId(trimToNull(decision.getRollbackSnapshotId()));
        campaign.setSendTimeOptimizationQuietHoursGatePassed(Boolean.TRUE.equals(decision.getQuietHoursGatePassed()));
        campaign.setSendTimeOptimizationApprovalGatePassed(Boolean.TRUE.equals(decision.getApprovalGatePassed()));
        campaign.setSendTimeOptimizationSuppressionGatePassed(Boolean.TRUE.equals(decision.getSuppressionGatePassed()));
        campaign.setSendTimeOptimizationWarmupGatePassed(Boolean.TRUE.equals(decision.getWarmupGatePassed()));
        campaign.setSendTimeOptimizationRateLimitGatePassed(Boolean.TRUE.equals(decision.getRateLimitGatePassed()));
        campaign.setSendTimeOptimizationProviderCapacityGatePassed(Boolean.TRUE.equals(decision.getProviderCapacityGatePassed()));
        campaign.setSendTimeOptimizationDeliverabilityGatePassed(Boolean.TRUE.equals(decision.getDeliverabilityGatePassed()));
    }

    private static CampaignDto.SendTimeOptimizationDecision fromCampaign(Campaign campaign) {
        return CampaignDto.SendTimeOptimizationDecision.builder()
                .optimizationType(campaign.getSendTimeOptimizationType())
                .policyKey(campaign.getSendTimeOptimizationPolicyKey())
                .optimizationRunId(campaign.getSendTimeOptimizationRunId())
                .snapshotHash(campaign.getSendTimeOptimizationSnapshotHash())
                .originalScheduledAt(campaign.getSendTimeOptimizationOriginalScheduledAt())
                .recommendedScheduledAt(campaign.getSendTimeOptimizationRecommendedScheduledAt())
                .timezone(campaign.getSendTimeOptimizationTimezone())
                .confidenceBand(campaign.getSendTimeOptimizationConfidenceBand())
                .fallbackMode(campaign.getSendTimeOptimizationFallbackMode())
                .blockedReasons(campaign.getSendTimeOptimizationBlockedReasons())
                .dataQualityReasons(campaign.getSendTimeOptimizationDataQualityReasons())
                .reasonCodes(campaign.getSendTimeOptimizationReasonCodes())
                .approvalRequired(campaign.isSendTimeOptimizationApprovalRequired())
                .rollbackRequired(campaign.isSendTimeOptimizationRollbackRequired())
                .approved(campaign.isSendTimeOptimizationApproved())
                .approvalId(campaign.getSendTimeOptimizationApprovalId())
                .approvedBy(campaign.getSendTimeOptimizationApprovedBy())
                .approvedAt(campaign.getSendTimeOptimizationApprovedAt())
                .rollbackSnapshotId(campaign.getSendTimeOptimizationRollbackSnapshotId())
                .quietHoursGatePassed(campaign.isSendTimeOptimizationQuietHoursGatePassed())
                .approvalGatePassed(campaign.isSendTimeOptimizationApprovalGatePassed())
                .suppressionGatePassed(campaign.isSendTimeOptimizationSuppressionGatePassed())
                .warmupGatePassed(campaign.isSendTimeOptimizationWarmupGatePassed())
                .rateLimitGatePassed(campaign.isSendTimeOptimizationRateLimitGatePassed())
                .providerCapacityGatePassed(campaign.isSendTimeOptimizationProviderCapacityGatePassed())
                .deliverabilityGatePassed(campaign.isSendTimeOptimizationDeliverabilityGatePassed())
                .build();
    }

    private static boolean hasStoredDecision(Campaign campaign) {
        return campaign != null
                && (!isBlank(campaign.getSendTimeOptimizationPolicyKey())
                || !isBlank(campaign.getSendTimeOptimizationRunId())
                || !isBlank(campaign.getSendTimeOptimizationSnapshotHash())
                || campaign.getSendTimeOptimizationRecommendedScheduledAt() != null
                || campaign.isSendTimeOptimizationApproved());
    }

    private static String requireText(String value, String field, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ValidationException(field, message);
        }
        return normalized;
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String normalize(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
