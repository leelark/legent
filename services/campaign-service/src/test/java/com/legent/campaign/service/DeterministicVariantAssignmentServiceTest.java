package com.legent.campaign.service;

import java.math.BigDecimal;
import java.util.List;

import com.legent.campaign.domain.CampaignExperiment;
import com.legent.campaign.domain.CampaignVariant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicVariantAssignmentServiceTest {

    private final DeterministicVariantAssignmentService assignmentService = new DeterministicVariantAssignmentService();

    @Test
    void assignsSameSubscriberToSameVariantDeterministically() {
        CampaignExperiment experiment = experiment("exp-1", CampaignExperiment.ExperimentStatus.ACTIVE, BigDecimal.ZERO);
        CampaignVariant control = variant("var-a", "A", 25);
        CampaignVariant challenger = variant("var-b", "B", 75);

        var first = assignmentService.assign(experiment, List.of(challenger, control), "subscriber-123");
        var second = assignmentService.assign(experiment, List.of(control, challenger), "subscriber-123");

        assertThat(first.variantId()).isEqualTo(second.variantId());
        assertThat(first.bucket()).isEqualTo(second.bucket());
        assertThat(first.holdout()).isFalse();
    }

    @Test
    void honorsNoSendHoldoutBeforeVariantAssignment() {
        CampaignExperiment experiment = experiment("exp-1", CampaignExperiment.ExperimentStatus.ACTIVE, new BigDecimal("100.00"));
        CampaignVariant control = variant("var-a", "A", 50);
        CampaignVariant challenger = variant("var-b", "B", 50);

        var assignment = assignmentService.assign(experiment, List.of(control, challenger), "subscriber-123");

        assertThat(assignment.holdout()).isTrue();
        assertThat(assignment.variantId()).isNull();
        assertThat(assignment.experimentId()).isEqualTo("exp-1");
    }

    @Test
    void promotedExperimentRoutesEveryoneToWinner() {
        CampaignExperiment experiment = experiment("exp-1", CampaignExperiment.ExperimentStatus.PROMOTED, BigDecimal.ZERO);
        experiment.setWinnerVariantId("var-b");
        CampaignVariant control = variant("var-a", "A", 50);
        CampaignVariant challenger = variant("var-b", "B", 50);

        var assignment = assignmentService.assign(experiment, List.of(control, challenger), "subscriber-123");

        assertThat(assignment.variantId()).isEqualTo("var-b");
        assertThat(assignment.variantKey()).isEqualTo("B");
        assertThat(assignment.holdout()).isFalse();
    }

    private CampaignExperiment experiment(String id,
                                          CampaignExperiment.ExperimentStatus status,
                                          BigDecimal holdout) {
        CampaignExperiment experiment = new CampaignExperiment();
        experiment.setId(id);
        experiment.setTenantId("tenant-1");
        experiment.setWorkspaceId("workspace-1");
        experiment.setCampaignId("campaign-1");
        experiment.setName("Subject test");
        experiment.setStatus(status);
        experiment.setHoldoutPercentage(holdout);
        return experiment;
    }

    private CampaignVariant variant(String id, String key, int weight) {
        CampaignVariant variant = new CampaignVariant();
        variant.setId(id);
        variant.setTenantId("tenant-1");
        variant.setWorkspaceId("workspace-1");
        variant.setCampaignId("campaign-1");
        variant.setExperimentId("exp-1");
        variant.setVariantKey(key);
        variant.setName(key);
        variant.setWeight(weight);
        variant.setActive(true);
        return variant;
    }
}
