package com.legent.audience.service;

import com.legent.audience.domain.Segment;
import com.legent.audience.dto.SegmentDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PredictiveSegmentGovernanceService {

    public static final String GOVERNANCE_KEY = "predictiveGovernance";

    private static final int MAX_FRESHNESS_DAYS = 90;
    private static final long MIN_ELIGIBLE_CONTACTS = 500;
    private static final long MIN_HISTORICAL_EVENTS = 1_000;

    private static final Set<String> ALLOWED_FEATURE_SOURCES = Set.of(
            "SUBSCRIBER_PROFILE",
            "SUBSCRIBER_ATTRIBUTES",
            "ENGAGEMENT_EVENTS",
            "CAMPAIGN_METRICS",
            "LIST_MEMBERSHIP",
            "SEGMENT_MEMBERSHIP",
            "PREFERENCE_STATUS",
            "SUPPRESSION_STATUS",
            "DATA_EXTENSION_FIELD"
    );

    private static final Set<String> PROHIBITED_DATA_CLASSES = Set.of(
            "PROTECTED_CLASS",
            "SENSITIVE_CATEGORY",
            "RACE",
            "RELIGION",
            "POLITICAL_AFFILIATION",
            "HEALTH_DATA",
            "PAYMENT_DATA",
            "RAW_CREDENTIALS",
            "SECRETS",
            "ACCESS_TOKEN",
            "REFRESH_TOKEN",
            "PRIVATE_KEY"
    );

    private static final Set<String> ALLOWED_DERIVATION_MODES = Set.of(
            "MODEL_BACKED",
            "RULE_DERIVED_FALLBACK"
    );

    public SegmentDto.PredictivePreviewResponse preview(SegmentDto.PredictivePreviewRequest request) {
        AudienceScope.tenantId();
        AudienceScope.workspaceId();
        return evaluate(request);
    }

    public static void validateForPersist(Segment segment) {
        if (segment == null) {
            throw new IllegalArgumentException("Segment is required");
        }

        boolean predictiveType = Segment.SegmentType.PREDICTIVE.equals(segment.getSegmentType());
        boolean carriesPredictiveGovernance = governanceMap(segment.getRules()) != null;
        if (!predictiveType) {
            if (carriesPredictiveGovernance) {
                throw new IllegalArgumentException("predictiveGovernance is allowed only for PREDICTIVE segments");
            }
            return;
        }

        if (segment.isScheduleEnabled()) {
            throw new IllegalArgumentException("Predictive segments cannot use scheduled recompute without a separate approved automation policy");
        }
        if (!hasAnyRuleCriteria(segment.getRules())) {
            throw new IllegalArgumentException("Predictive segments require explicit rule criteria from the approved preview");
        }

        SegmentDto.PredictivePreviewResponse preview = evaluate(requestFromGovernance(segment.getRules()));
        if (!preview.isApplyAllowed()) {
            throw new IllegalArgumentException(
                    "Predictive segment governance is not approved: "
                            + String.join("; ", preview.getBlockedReasons()));
        }
    }

    public static void requireApprovedForMaterialization(Segment segment) {
        if (segment != null && Segment.SegmentType.PREDICTIVE.equals(segment.getSegmentType())) {
            validateForPersist(segment);
        }
    }

    public static String derivationMode(Segment segment) {
        if (segment == null || !Segment.SegmentType.PREDICTIVE.equals(segment.getSegmentType())) {
            return "RULE_DERIVED";
        }
        Map<String, Object> governance = governanceMap(segment.getRules());
        String mode = governance == null ? null : stringValue(governance.get("derivationMode"));
        return normalizeMode(mode);
    }

    public static Map<String, Object> responseGovernance(Segment segment) {
        if (segment == null || !Segment.SegmentType.PREDICTIVE.equals(segment.getSegmentType())) {
            return null;
        }
        Map<String, Object> governance = governanceMap(segment.getRules());
        if (governance == null) {
            return null;
        }
        return new LinkedHashMap<>(governance);
    }

    private static SegmentDto.PredictivePreviewResponse evaluate(SegmentDto.PredictivePreviewRequest request) {
        List<String> blocked = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request == null) {
            blocked.add("PREDICTIVE_GOVERNANCE_REQUIRED");
            return blockedResponse(blocked);
        }

        List<String> featureSources = normalizeList(request.getFeatureSources());
        List<String> dataClassesUsed = normalizeList(request.getDataClassesUsed());
        List<String> excludedDataClasses = normalizeList(request.getExcludedDataClasses());
        List<String> reasonCodes = cleanList(request.getReasonCodes());
        String rawDerivationMode = trimToNull(request.getDerivationMode());
        String derivationMode = normalizeMode(rawDerivationMode);
        long eligibleContacts = safeLong(request.getEligibleContactCount());
        long historicalEvents = safeLong(request.getHistoricalEventCount());
        long modeledCount = safeLong(request.getModeledCount());
        long suppressionImpact = safeLong(request.getSuppressionImpactCount());
        int freshnessDays = safeInt(request.getDataFreshnessDays());

        if (!Boolean.TRUE.equals(request.getTenantPolicyEnabled())) {
            blocked.add("TENANT_AI_POLICY_NOT_ENABLED");
        }
        if (isBlank(request.getPolicyVersion())) {
            blocked.add("POLICY_VERSION_REQUIRED");
        }
        if (rawDerivationMode == null) {
            blocked.add("DERIVATION_MODE_REQUIRED");
        } else if (!ALLOWED_DERIVATION_MODES.contains(derivationMode)) {
            blocked.add("UNSUPPORTED_DERIVATION_MODE");
        }
        if (featureSources.isEmpty()) {
            blocked.add("FEATURE_SOURCES_REQUIRED");
        }
        for (String source : featureSources) {
            if (!ALLOWED_FEATURE_SOURCES.contains(source)) {
                blocked.add("UNSUPPORTED_FEATURE_SOURCE:" + source);
            }
        }
        for (String dataClass : dataClassesUsed) {
            if (PROHIBITED_DATA_CLASSES.contains(dataClass)) {
                blocked.add("PROHIBITED_DATA_CLASS:" + dataClass);
            }
        }
        if (!Boolean.TRUE.equals(request.getProtectedDataExcluded())) {
            blocked.add("PROTECTED_DATA_EXCLUSION_REQUIRED");
        }
        if (freshnessDays < 1 || freshnessDays > MAX_FRESHNESS_DAYS) {
            blocked.add("DATA_FRESHNESS_OUT_OF_RANGE");
        }
        if (eligibleContacts < MIN_ELIGIBLE_CONTACTS) {
            blocked.add("LOW_ELIGIBLE_CONTACT_COUNT");
        }
        if (historicalEvents < MIN_HISTORICAL_EVENTS) {
            blocked.add("LOW_HISTORICAL_EVENT_COUNT");
        }
        if (!Boolean.TRUE.equals(request.getBiasDriftCheckPassed())) {
            blocked.add("BIAS_DRIFT_CHECK_NOT_PASSED");
        }
        if (reasonCodes.isEmpty()) {
            blocked.add("REASON_CODES_REQUIRED");
        }
        if (isBlank(request.getRollbackSnapshotId())) {
            blocked.add("ROLLBACK_SNAPSHOT_REQUIRED");
        }
        if (!"APPROVED".equals(normalizeToken(request.getApprovalStatus()))) {
            blocked.add("HUMAN_APPROVAL_REQUIRED");
        }
        if (isBlank(request.getApprovedBy()) || isBlank(request.getApprovedAt())) {
            blocked.add("APPROVAL_AUDIT_REQUIRED");
        }
        if (modeledCount <= 0) {
            blocked.add("PREVIEW_COUNT_REQUIRED");
        }
        if (suppressionImpact > modeledCount) {
            blocked.add("SUPPRESSION_IMPACT_EXCEEDS_PREVIEW");
        }

        long netEligible = Math.max(0, modeledCount - suppressionImpact);
        if (netEligible == 0) {
            blocked.add("NET_ELIGIBLE_COUNT_EMPTY");
        }
        if ("RULE_DERIVED_FALLBACK".equals(derivationMode)) {
            warnings.add("Prediction is labeled as rule-derived fallback and must not be described as model-backed.");
        }
        if (suppressionImpact > 0 && modeledCount > 0 && suppressionImpact * 5 > modeledCount) {
            warnings.add("Suppression impact exceeds 20 percent of previewed recipients.");
        }

        boolean valid = blocked.isEmpty();
        return SegmentDto.PredictivePreviewResponse.builder()
                .valid(valid)
                .applyAllowed(valid)
                .approvalRequired(true)
                .derivationMode(derivationMode)
                .confidenceBand(confidenceBand(valid, eligibleContacts, historicalEvents, freshnessDays))
                .riskBand(riskBand(valid, modeledCount, suppressionImpact))
                .policyVersion(trimToNull(request.getPolicyVersion()))
                .featureSources(featureSources)
                .dataClassesUsed(dataClassesUsed)
                .excludedDataClasses(excludedDataClasses)
                .eligibleContactCount(eligibleContacts)
                .historicalEventCount(historicalEvents)
                .dataFreshnessDays(freshnessDays)
                .previewCount(modeledCount)
                .suppressionImpactCount(suppressionImpact)
                .netEligibleCount(netEligible)
                .approvalStatus(normalizeToken(request.getApprovalStatus()))
                .rollbackSnapshotId(trimToNull(request.getRollbackSnapshotId()))
                .rollbackSnapshotStatus(isBlank(request.getRollbackSnapshotId()) ? "REQUIRED_BEFORE_APPLY" : "CAPTURED")
                .reasonCodes(reasonCodes)
                .blockedReasons(deduplicate(blocked))
                .warnings(warnings)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static SegmentDto.PredictivePreviewRequest requestFromGovernance(Map<String, Object> rules) {
        Map<String, Object> governance = governanceMap(rules);
        if (governance == null) {
            throw new IllegalArgumentException("PREDICTIVE segments require predictiveGovernance");
        }
        return SegmentDto.PredictivePreviewRequest.builder()
                .featureSources(stringList(governance.get("featureSources")))
                .dataClassesUsed(stringList(governance.get("dataClassesUsed")))
                .excludedDataClasses(stringList(governance.get("excludedDataClasses")))
                .eligibleContactCount(longValue(governance.get("eligibleContactCount")))
                .historicalEventCount(longValue(governance.get("historicalEventCount")))
                .modeledCount(longValue(governance.get("modeledCount")))
                .suppressionImpactCount(longValue(governance.get("suppressionImpactCount")))
                .dataFreshnessDays(intValue(governance.get("dataFreshnessDays")))
                .tenantPolicyEnabled(booleanValue(governance.get("tenantPolicyEnabled")))
                .policyVersion(stringValue(governance.get("policyVersion")))
                .protectedDataExcluded(booleanValue(governance.get("protectedDataExcluded")))
                .biasDriftCheckPassed(booleanValue(governance.get("biasDriftCheckPassed")))
                .derivationMode(stringValue(governance.get("derivationMode")))
                .approvalStatus(stringValue(governance.get("approvalStatus")))
                .approvedBy(stringValue(governance.get("approvedBy")))
                .approvedAt(stringValue(governance.get("approvedAt")))
                .rollbackSnapshotId(stringValue(governance.get("rollbackSnapshotId")))
                .reasonCodes(stringList(governance.get("reasonCodes")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> governanceMap(Map<String, Object> rules) {
        if (rules == null) {
            return null;
        }
        Object governance = rules.get(GOVERNANCE_KEY);
        if (governance instanceof Map<?, ?> rawMap) {
            Map<String, Object> converted = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key instanceof String text) {
                    converted.put(text, value);
                }
            });
            return converted;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasAnyRuleCriteria(Map<String, Object> rules) {
        if (rules == null) {
            return false;
        }
        Object conditions = rules.get("conditions");
        if (conditions instanceof List<?> list && !list.isEmpty()) {
            return true;
        }
        Object groups = rules.get("groups");
        if (groups instanceof List<?> list) {
            for (Object group : list) {
                if (group instanceof Map<?, ?> rawGroup && hasAnyRuleCriteria((Map<String, Object>) rawGroup)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static SegmentDto.PredictivePreviewResponse blockedResponse(List<String> blocked) {
        return SegmentDto.PredictivePreviewResponse.builder()
                .valid(false)
                .applyAllowed(false)
                .approvalRequired(true)
                .derivationMode("MODEL_BACKED")
                .confidenceBand("LOW")
                .riskBand("BLOCKED")
                .blockedReasons(deduplicate(blocked))
                .warnings(List.of())
                .reasonCodes(List.of())
                .featureSources(List.of())
                .dataClassesUsed(List.of())
                .excludedDataClasses(List.of())
                .rollbackSnapshotStatus("REQUIRED_BEFORE_APPLY")
                .build();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String token = normalizeToken(value);
            if (token != null) {
                normalized.add(token);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            String text = trimToNull(value);
            if (text != null) {
                cleaned.add(text);
            }
        }
        return List.copyOf(cleaned);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text) {
                values.add(text);
            }
        }
        return values;
    }

    private static List<String> deduplicate(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private static String confidenceBand(boolean valid, long eligibleContacts, long historicalEvents, int freshnessDays) {
        if (!valid) {
            return "LOW";
        }
        if (eligibleContacts >= (MIN_ELIGIBLE_CONTACTS * 2)
                && historicalEvents >= (MIN_HISTORICAL_EVENTS * 2)
                && freshnessDays <= 30) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private static String riskBand(boolean valid, long modeledCount, long suppressionImpact) {
        if (!valid) {
            return "BLOCKED";
        }
        if (suppressionImpact > 0 && modeledCount > 0 && suppressionImpact * 5 > modeledCount) {
            return "ELEVATED";
        }
        return "LOW";
    }

    private static String normalizeMode(String value) {
        String normalized = normalizeToken(value);
        return normalized == null ? "MODEL_BACKED" : normalized;
    }

    private static String normalizeToken(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return null;
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
