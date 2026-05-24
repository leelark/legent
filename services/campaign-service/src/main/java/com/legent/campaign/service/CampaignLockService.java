package com.legent.campaign.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignLock;
import com.legent.campaign.repository.CampaignBudgetRepository;
import com.legent.campaign.repository.CampaignExperimentRepository;
import com.legent.campaign.repository.CampaignFrequencyPolicyRepository;
import com.legent.campaign.repository.CampaignLockRepository;
import com.legent.campaign.repository.CampaignVariantRepository;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignLockService {

    private final CampaignLockRepository lockRepository;
    private final CampaignExperimentRepository experimentRepository;
    private final CampaignVariantRepository variantRepository;
    private final CampaignBudgetRepository budgetRepository;
    private final CampaignFrequencyPolicyRepository frequencyPolicyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CampaignLock lockCampaign(Campaign campaign) {
        supersedeActiveLock(campaign);
        String snapshot = snapshot(campaign);
        CampaignLock lock = new CampaignLock();
        lock.setTenantId(campaign.getTenantId());
        lock.setWorkspaceId(campaign.getWorkspaceId());
        lock.setCampaignId(campaign.getId());
        lock.setLockHash(sha256(snapshot));
        lock.setSnapshot(snapshot);
        lock.setLockedAt(Instant.now());
        lock.setLockedBy(TenantContext.getUserId());
        return lockRepository.save(lock);
    }

    @Transactional(readOnly = true)
    public void validateActiveLock(Campaign campaign) {
        lockRepository.findFirstByTenantIdAndWorkspaceIdAndCampaignIdAndStatusAndDeletedAtIsNullOrderByLockedAtDesc(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), CampaignLock.STATUS_ACTIVE)
                .ifPresent(lock -> {
                    String currentHash = sha256(snapshot(campaign));
                    if (!lock.getLockHash().equals(currentHash)) {
                        throw new ValidationException("campaign.lock", "Approved campaign lock no longer matches campaign send-critical state");
                    }
                });
    }

    @Transactional
    public boolean supersedeIfChanged(Campaign campaign) {
        return lockRepository.findFirstByTenantIdAndWorkspaceIdAndCampaignIdAndStatusAndDeletedAtIsNullOrderByLockedAtDesc(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), CampaignLock.STATUS_ACTIVE)
                .map(lock -> {
                    if (lock.getLockHash().equals(sha256(snapshot(campaign)))) {
                        return false;
                    }
                    lock.setStatus(CampaignLock.STATUS_SUPERSEDED);
                    lock.setSupersededAt(Instant.now());
                    lockRepository.save(lock);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void supersedeActiveLock(Campaign campaign) {
        lockRepository.findFirstByTenantIdAndWorkspaceIdAndCampaignIdAndStatusAndDeletedAtIsNullOrderByLockedAtDesc(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), CampaignLock.STATUS_ACTIVE)
                .ifPresent(lock -> {
                    lock.setStatus(CampaignLock.STATUS_SUPERSEDED);
                    lock.setSupersededAt(Instant.now());
                    lockRepository.save(lock);
                });
    }

    private String snapshot(Campaign campaign) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("contentId", campaign.getContentId());
            root.put("subject", campaign.getSubject());
            root.put("preheader", campaign.getPreheader());
            root.put("senderProfileId", campaign.getSenderProfileId());
            root.put("senderName", campaign.getSenderName());
            root.put("senderEmail", campaign.getSenderEmail());
            root.put("replyToEmail", campaign.getReplyToEmail());
            root.put("providerId", campaign.getProviderId());
            root.put("sendingDomain", campaign.getSendingDomain());
            root.put("frequencyCap", campaign.getFrequencyCap());
            root.put("scheduledAt", campaign.getScheduledAt() != null ? campaign.getScheduledAt().toString() : null);
            if (hasSendTimeOptimizationEvidence(campaign)) {
                root.put("sendTimeOptimization", Map.ofEntries(
                        Map.entry("policyKey", campaign.getSendTimeOptimizationPolicyKey() == null ? "" : campaign.getSendTimeOptimizationPolicyKey()),
                        Map.entry("optimizationType", campaign.getSendTimeOptimizationType() == null ? "" : campaign.getSendTimeOptimizationType()),
                        Map.entry("optimizationRunId", campaign.getSendTimeOptimizationRunId() == null ? "" : campaign.getSendTimeOptimizationRunId()),
                        Map.entry("snapshotHash", campaign.getSendTimeOptimizationSnapshotHash() == null ? "" : campaign.getSendTimeOptimizationSnapshotHash()),
                        Map.entry("originalScheduledAt", campaign.getSendTimeOptimizationOriginalScheduledAt() == null ? "" : campaign.getSendTimeOptimizationOriginalScheduledAt().toString()),
                        Map.entry("recommendedScheduledAt", campaign.getSendTimeOptimizationRecommendedScheduledAt() == null ? "" : campaign.getSendTimeOptimizationRecommendedScheduledAt().toString()),
                        Map.entry("timezone", campaign.getSendTimeOptimizationTimezone() == null ? "" : campaign.getSendTimeOptimizationTimezone()),
                        Map.entry("campaignTimezone", campaign.getTimezone() == null ? "" : campaign.getTimezone()),
                        Map.entry("quietHoursStart", campaign.getQuietHoursStart() == null ? "" : campaign.getQuietHoursStart().toString()),
                        Map.entry("quietHoursEnd", campaign.getQuietHoursEnd() == null ? "" : campaign.getQuietHoursEnd().toString()),
                        Map.entry("sendWindowStart", campaign.getSendWindowStart() == null ? "" : campaign.getSendWindowStart().toString()),
                        Map.entry("sendWindowEnd", campaign.getSendWindowEnd() == null ? "" : campaign.getSendWindowEnd().toString()),
                        Map.entry("confidenceBand", campaign.getSendTimeOptimizationConfidenceBand() == null ? "" : campaign.getSendTimeOptimizationConfidenceBand()),
                        Map.entry("fallbackMode", campaign.getSendTimeOptimizationFallbackMode() == null ? "" : campaign.getSendTimeOptimizationFallbackMode()),
                        Map.entry("blockedReasons", campaign.getSendTimeOptimizationBlockedReasons() == null ? java.util.List.of() : campaign.getSendTimeOptimizationBlockedReasons()),
                        Map.entry("dataQualityReasons", campaign.getSendTimeOptimizationDataQualityReasons() == null ? java.util.List.of() : campaign.getSendTimeOptimizationDataQualityReasons()),
                        Map.entry("reasonCodes", campaign.getSendTimeOptimizationReasonCodes() == null ? java.util.List.of() : campaign.getSendTimeOptimizationReasonCodes()),
                        Map.entry("approvalRequired", campaign.isSendTimeOptimizationApprovalRequired()),
                        Map.entry("rollbackRequired", campaign.isSendTimeOptimizationRollbackRequired()),
                        Map.entry("approved", campaign.isSendTimeOptimizationApproved()),
                        Map.entry("approvalId", campaign.getSendTimeOptimizationApprovalId() == null ? "" : campaign.getSendTimeOptimizationApprovalId()),
                        Map.entry("approvedBy", campaign.getSendTimeOptimizationApprovedBy() == null ? "" : campaign.getSendTimeOptimizationApprovedBy()),
                        Map.entry("approvedAt", campaign.getSendTimeOptimizationApprovedAt() == null ? "" : campaign.getSendTimeOptimizationApprovedAt().toString()),
                        Map.entry("rollbackSnapshotId", campaign.getSendTimeOptimizationRollbackSnapshotId() == null ? "" : campaign.getSendTimeOptimizationRollbackSnapshotId()),
                        Map.entry("quietHoursGatePassed", campaign.isSendTimeOptimizationQuietHoursGatePassed()),
                        Map.entry("approvalGatePassed", campaign.isSendTimeOptimizationApprovalGatePassed()),
                        Map.entry("suppressionGatePassed", campaign.isSendTimeOptimizationSuppressionGatePassed()),
                        Map.entry("warmupGatePassed", campaign.isSendTimeOptimizationWarmupGatePassed()),
                        Map.entry("rateLimitGatePassed", campaign.isSendTimeOptimizationRateLimitGatePassed()),
                        Map.entry("providerCapacityGatePassed", campaign.isSendTimeOptimizationProviderCapacityGatePassed()),
                        Map.entry("deliverabilityGatePassed", campaign.isSendTimeOptimizationDeliverabilityGatePassed())
                ));
            }
            root.put("audiences", campaign.getAudiences() == null ? java.util.List.of() : campaign.getAudiences().stream()
                    .sorted(Comparator.comparing(a -> a.getAudienceType().name() + ":" + a.getAudienceId() + ":" + a.getAction().name()))
                    .map(a -> Map.of(
                            "type", a.getAudienceType().name(),
                            "id", a.getAudienceId(),
                            "action", a.getAction().name()))
                    .toList());
            root.put("experiments", experimentRepository
                    .findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                            campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId())
                    .stream()
                    .sorted(Comparator.comparing(e -> e.getId() == null ? "" : e.getId()))
                    .map(experiment -> {
                        Map<String, Object> exp = new LinkedHashMap<>();
                        exp.put("id", experiment.getId());
                        exp.put("name", experiment.getName());
                        exp.put("type", experiment.getExperimentType().name());
                        exp.put("status", experiment.getStatus().name());
                        exp.put("metric", experiment.getWinnerMetric().name());
                        exp.put("holdout", experiment.getHoldoutPercentage());
                        exp.put("variants", variantRepository
                                .findByTenantIdAndWorkspaceIdAndExperimentIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                                        campaign.getTenantId(), campaign.getWorkspaceId(), experiment.getId())
                                .stream()
                                .map(v -> Map.of(
                                        "key", v.getVariantKey(),
                                        "weight", v.getWeight(),
                                        "contentId", v.getContentId() == null ? "" : v.getContentId(),
                                        "subject", v.getSubjectOverride() == null ? "" : v.getSubjectOverride(),
                                        "active", v.isActive(),
                                        "holdout", v.isHoldoutVariant()))
                                .toList());
                        return exp;
                    }).toList());
            root.put("budget", budgetRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                    campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId()).orElse(null));
            root.put("frequency", frequencyPolicyRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                    campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId()).orElse(null));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new ValidationException("campaign.lock", "Unable to build campaign lock snapshot: " + e.getMessage());
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new ValidationException("campaign.lock", "Unable to hash campaign lock snapshot");
        }
    }

    private boolean hasSendTimeOptimizationEvidence(Campaign campaign) {
        return campaign.getSendTimeOptimizationRecommendedScheduledAt() != null
                || campaign.isSendTimeOptimizationApproved()
                || campaign.getSendTimeOptimizationRunId() != null
                || campaign.getSendTimeOptimizationSnapshotHash() != null;
    }
}
