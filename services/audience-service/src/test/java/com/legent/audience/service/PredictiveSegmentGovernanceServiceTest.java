package com.legent.audience.service;

import com.legent.audience.dto.SegmentDto;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PredictiveSegmentGovernanceServiceTest {

    private final PredictiveSegmentGovernanceService service = new PredictiveSegmentGovernanceService();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void previewRequiresTenantAndWorkspaceContext() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.preview(validRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context");
    }

    @Test
    void previewAllowsApprovedGovernedPredictiveContract() {
        SegmentDto.PredictivePreviewResponse response = service.preview(validRequest());

        assertThat(response.isValid()).isTrue();
        assertThat(response.isApplyAllowed()).isTrue();
        assertThat(response.getDerivationMode()).isEqualTo("MODEL_BACKED");
        assertThat(response.getFeatureSources()).containsExactly("SUBSCRIBER_PROFILE", "ENGAGEMENT_EVENTS", "SUPPRESSION_STATUS");
        assertThat(response.getPreviewCount()).isEqualTo(800);
        assertThat(response.getSuppressionImpactCount()).isEqualTo(100);
        assertThat(response.getNetEligibleCount()).isEqualTo(700);
        assertThat(response.getRollbackSnapshotStatus()).isEqualTo("CAPTURED");
        assertThat(response.getBlockedReasons()).isEmpty();
    }

    @Test
    void previewRejectsDisabledPolicyProtectedDataAndLowReadiness() {
        SegmentDto.PredictivePreviewResponse response = service.preview(SegmentDto.PredictivePreviewRequest.builder()
                .tenantPolicyEnabled(false)
                .policyVersion("")
                .derivationMode("model-backed")
                .featureSources(List.of("subscriber_profile", "race"))
                .dataClassesUsed(List.of("health_data"))
                .excludedDataClasses(List.of())
                .protectedDataExcluded(false)
                .eligibleContactCount(20L)
                .historicalEventCount(50L)
                .modeledCount(0L)
                .suppressionImpactCount(0L)
                .dataFreshnessDays(180)
                .biasDriftCheckPassed(false)
                .approvalStatus("pending")
                .approvedBy("")
                .approvedAt("")
                .rollbackSnapshotId("")
                .reasonCodes(List.of())
                .build());

        assertThat(response.isValid()).isFalse();
        assertThat(response.isApplyAllowed()).isFalse();
        assertThat(response.getRiskBand()).isEqualTo("BLOCKED");
        assertThat(response.getBlockedReasons())
                .contains("TENANT_AI_POLICY_NOT_ENABLED",
                        "POLICY_VERSION_REQUIRED",
                        "UNSUPPORTED_FEATURE_SOURCE:RACE",
                        "PROHIBITED_DATA_CLASS:HEALTH_DATA",
                        "PROTECTED_DATA_EXCLUSION_REQUIRED",
                        "LOW_ELIGIBLE_CONTACT_COUNT",
                        "LOW_HISTORICAL_EVENT_COUNT",
                        "BIAS_DRIFT_CHECK_NOT_PASSED",
                        "HUMAN_APPROVAL_REQUIRED",
                        "ROLLBACK_SNAPSHOT_REQUIRED");
    }

    static SegmentDto.PredictivePreviewRequest validRequest() {
        return SegmentDto.PredictivePreviewRequest.builder()
                .tenantPolicyEnabled(true)
                .policyVersion("ai-policy-v1")
                .derivationMode("model-backed")
                .featureSources(List.of("subscriber_profile", "engagement_events", "suppression_status"))
                .dataClassesUsed(List.of("subscriber_profile", "engagement_events", "suppression_status"))
                .excludedDataClasses(List.of("protected_class", "health_data", "payment_data"))
                .protectedDataExcluded(true)
                .eligibleContactCount(2_000L)
                .historicalEventCount(4_000L)
                .modeledCount(800L)
                .suppressionImpactCount(100L)
                .dataFreshnessDays(14)
                .biasDriftCheckPassed(true)
                .approvalStatus("approved")
                .approvedBy("approver-1")
                .approvedAt("2026-05-20T12:00:00Z")
                .rollbackSnapshotId("snapshot-1")
                .reasonCodes(List.of("HIGH_ENGAGEMENT_PROPENSITY", "RECENT_ACTIVITY"))
                .build();
    }
}
