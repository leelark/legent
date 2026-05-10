package com.legent.campaign.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.legent.campaign.domain.CampaignExperiment.ExperimentStatus;
import com.legent.campaign.domain.CampaignExperiment.ExperimentType;
import com.legent.campaign.domain.CampaignExperiment.WinnerMetric;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class CampaignEngineDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentResponse {
        private String id;
        private String campaignId;
        private String name;
        private ExperimentType experimentType;
        private ExperimentStatus status;
        private WinnerMetric winnerMetric;
        private String customMetricName;
        private boolean autoPromotion;
        private Integer minRecipientsPerVariant;
        private Integer evaluationWindowHours;
        private BigDecimal holdoutPercentage;
        private String winnerVariantId;
        private String factors;
        private Instant startsAt;
        private Instant endsAt;
        private Instant completedAt;
        private List<VariantResponse> variants;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentRequest {
        @NotBlank
        private String name;
        @NotNull
        private ExperimentType experimentType;
        @NotNull
        private WinnerMetric winnerMetric;
        private String customMetricName;
        private Boolean autoPromotion;
        @Min(1)
        private Integer minRecipientsPerVariant;
        @Min(1)
        private Integer evaluationWindowHours;
        @Min(0)
        @Max(95)
        private BigDecimal holdoutPercentage;
        private String factors;
        private ExperimentStatus status;
        @Valid
        private List<VariantRequest> variants;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantResponse {
        private String id;
        private String experimentId;
        private String variantKey;
        private String name;
        private Integer weight;
        private boolean controlVariant;
        private boolean holdoutVariant;
        private boolean active;
        private boolean winner;
        private String contentId;
        private String subjectOverride;
        private String metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantRequest {
        @NotBlank
        private String variantKey;
        @NotBlank
        private String name;
        @Min(0)
        @Max(100)
        private Integer weight;
        private Boolean controlVariant;
        private Boolean holdoutVariant;
        private Boolean active;
        private String contentId;
        private String subjectOverride;
        private String metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrequencyPolicyResponse {
        private String id;
        private String campaignId;
        private boolean enabled;
        private Integer maxSends;
        private Integer windowHours;
        private boolean includeJourneys;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrequencyPolicyRequest {
        private Boolean enabled;
        @Min(0)
        private Integer maxSends;
        @Min(1)
        private Integer windowHours;
        private Boolean includeJourneys;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetResponse {
        private String id;
        private String campaignId;
        private String currency;
        private BigDecimal budgetLimit;
        private BigDecimal costPerSend;
        private BigDecimal reservedSpend;
        private BigDecimal actualSpend;
        private boolean enforced;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetRequest {
        private String currency;
        @Min(0)
        private BigDecimal budgetLimit;
        @Min(0)
        private BigDecimal costPerSend;
        private Boolean enforced;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendPreflightReport {
        private String campaignId;
        private boolean sendAllowed;
        private List<String> errors;
        private List<String> warnings;
        private Map<String, Object> checks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendPlanResponse {
        private String campaignId;
        private String resendMode;
        private boolean requiresConfirmation;
        private String idempotencyKey;
        private long eligibleRecipients;
        private List<String> warnings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendPlanRequest {
        @NotBlank
        private String resendMode;
        private Boolean confirmed;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeadLetterResponse {
        private String id;
        private String campaignId;
        private String jobId;
        private String batchId;
        private String subscriberId;
        private String email;
        private String reason;
        private String payload;
        private Integer retryCount;
        private String status;
        private Instant nextRetryAt;
        private Instant replayedAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantMetricsResponse {
        private String campaignId;
        private String experimentId;
        private String variantId;
        private long targetCount;
        private long holdoutCount;
        private long sentCount;
        private long failedCount;
        private long openCount;
        private long clickCount;
        private long conversionCount;
        private BigDecimal revenue;
        private long customMetricCount;
        private BigDecimal score;
    }
}
