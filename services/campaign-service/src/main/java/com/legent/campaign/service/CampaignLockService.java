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
}
