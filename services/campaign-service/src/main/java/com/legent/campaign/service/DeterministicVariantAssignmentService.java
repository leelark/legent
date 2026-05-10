package com.legent.campaign.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;

import com.legent.campaign.domain.CampaignExperiment;
import com.legent.campaign.domain.CampaignVariant;
import org.springframework.stereotype.Service;

@Service
public class DeterministicVariantAssignmentService {

    public Assignment assign(CampaignExperiment experiment,
                             List<CampaignVariant> variants,
                             String subscriberKey) {
        if (experiment == null || variants == null || variants.isEmpty()) {
            return Assignment.noExperiment();
        }

        List<CampaignVariant> activeVariants = variants.stream()
                .filter(CampaignVariant::isActive)
                .sorted(Comparator.comparing(CampaignVariant::getVariantKey, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (activeVariants.isEmpty()) {
            return Assignment.noExperiment();
        }

        if (experiment.getWinnerVariantId() != null
                && experiment.getStatus() == CampaignExperiment.ExperimentStatus.PROMOTED) {
            return activeVariants.stream()
                    .filter(variant -> experiment.getWinnerVariantId().equals(variant.getId()))
                    .findFirst()
                    .map(variant -> Assignment.variant(experiment, variant, bucket(experiment, subscriberKey)))
                    .orElseGet(Assignment::noExperiment);
        }

        int bucket = bucket(experiment, subscriberKey);
        BigDecimal holdout = experiment.getHoldoutPercentage() == null
                ? BigDecimal.ZERO : experiment.getHoldoutPercentage();
        int holdoutBuckets = holdout.movePointRight(2).intValue();
        if (holdoutBuckets > 0 && bucket < holdoutBuckets) {
            return Assignment.holdout(experiment, bucket);
        }

        List<CampaignVariant> sendVariants = activeVariants.stream()
                .filter(variant -> !variant.isHoldoutVariant())
                .toList();
        if (sendVariants.isEmpty()) {
            return Assignment.holdout(experiment, bucket);
        }

        int normalizedBucket = Math.floorMod(bucket - holdoutBuckets, 10000 - Math.min(holdoutBuckets, 9999));
        int totalWeight = sendVariants.stream()
                .map(CampaignVariant::getWeight)
                .filter(weight -> weight != null && weight > 0)
                .mapToInt(Integer::intValue)
                .sum();
        if (totalWeight <= 0) {
            return Assignment.variant(experiment, sendVariants.get(0), bucket);
        }

        int target = (int) Math.floor((normalizedBucket / 10000.0) * totalWeight);
        int cumulative = 0;
        for (CampaignVariant variant : sendVariants) {
            int weight = variant.getWeight() == null ? 0 : Math.max(0, variant.getWeight());
            cumulative += weight;
            if (target < cumulative) {
                return Assignment.variant(experiment, variant, bucket);
            }
        }
        return Assignment.variant(experiment, sendVariants.get(sendVariants.size() - 1), bucket);
    }

    private int bucket(CampaignExperiment experiment, String subscriberKey) {
        String input = String.join(":",
                safe(experiment.getTenantId()),
                safe(experiment.getWorkspaceId()),
                safe(experiment.getCampaignId()),
                safe(experiment.getId()),
                safe(subscriberKey));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, hash).mod(BigInteger.valueOf(10000)).intValue();
        } catch (Exception e) {
            return Math.floorMod(input.hashCode(), 10000);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record Assignment(String experimentId,
                             String variantId,
                             String variantKey,
                             String contentId,
                             String subjectOverride,
                             boolean holdout,
                             int bucket) {
        public static Assignment noExperiment() {
            return new Assignment(null, null, null, null, null, false, -1);
        }

        static Assignment holdout(CampaignExperiment experiment, int bucket) {
            return new Assignment(experiment.getId(), null, "HOLDOUT", null, null, true, bucket);
        }

        static Assignment variant(CampaignExperiment experiment, CampaignVariant variant, int bucket) {
            return new Assignment(
                    experiment.getId(),
                    variant.getId(),
                    variant.getVariantKey(),
                    variant.getContentId(),
                    variant.getSubjectOverride(),
                    false,
                    bucket);
        }
    }
}
