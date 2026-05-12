package com.legent.foundation.service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.PersonalizationEvaluateRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class RealtimePersonalizationService extends PerformanceLedgerSupport {

    public RealtimePersonalizationService(CorePlatformRepository repository, ObjectMapper objectMapper) {
        super(repository, objectMapper);
    }

    @Transactional
    public Map<String, Object> evaluate(PersonalizationEvaluateRequest request) {
        long started = System.nanoTime();
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> profile = safeMap(request.getProfile());
        Map<String, Object> event = safeMap(request.getEvent());
        String eventType = normalize(defaultValue(request.getEventType(), "PROFILE_UPDATED"));
        String subjectId = firstText(request.getSubjectId(), asString(profile.get("subjectId")), asString(profile.get("id")));
        List<Map<String, Object>> segmentHits = evaluateSegments(request.getSegmentRules(), profile, event);
        List<Map<String, Object>> guardrailFindings = guardrailFindings(profile, event, safeMap(request.getGuardrails()));
        Map<String, Object> variantDecision = selectVariant(request.getVariants(), segmentHits, profile, event, guardrailFindings);
        Map<String, Object> personalization = personalizationPayload(request.getPersonalizationDefaults(), variantDecision, segmentHits, profile);
        long measuredMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        int latencyMs = Math.max((int) Math.min(Integer.MAX_VALUE, measuredMs),
                request.getSimulatedLatencyMs() == null ? 0 : request.getSimulatedLatencyMs());
        boolean sloPass = latencyMs < 1000;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("evaluationKey", defaultValue(request.getEvaluationKey(), "performance-personalization"));
        response.put("subjectId", subjectId);
        response.put("eventType", eventType);
        response.put("segmentHits", segmentHits);
        response.put("variantDecision", variantDecision);
        response.put("personalization", personalization);
        response.put("guardrailFindings", guardrailFindings);
        response.put("latencyMs", latencyMs);
        response.put("sloPass", sloPass);
        response.put("evaluatedAt", Instant.now().toString());

        Map<String, Object> values = baseValues(workspaceId);
        values.put("region", blankToNull(request.getRegion()));
        values.put("evaluation_key", response.get("evaluationKey"));
        values.put("subject_id", subjectId);
        values.put("event_type", eventType);
        values.put("input_profile", toJson(profile));
        values.put("event_payload", toJson(event));
        values.put("segment_hits", toJson(segmentHits));
        values.put("variant_decision", toJson(variantDecision));
        values.put("personalization", toJson(personalization));
        values.put("guardrail_findings", toJson(guardrailFindings));
        values.put("latency_ms", latencyMs);
        values.put("slo_pass", sloPass);
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("personalization_evaluation_runs", values,
                List.of("input_profile", "event_payload", "segment_hits", "variant_decision", "personalization", "guardrail_findings", "metadata"));
        response.put("id", saved.get("id"));
        return response;
    }

    private List<Map<String, Object>> evaluateSegments(List<Map<String, Object>> segmentRules,
                                                       Map<String, Object> profile,
                                                       Map<String, Object> event) {
        if (segmentRules == null || segmentRules.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map<String, Object> segment : segmentRules) {
            Map<String, Object> rules = readNestedRule(segment);
            if (matchRuleGroup(rules, profile, event)) {
                hits.add(map(
                        "key", defaultValue(asString(segment.get("key")), defaultValue(asString(segment.get("segmentKey")), "segment-" + (hits.size() + 1))),
                        "name", defaultValue(asString(segment.get("name")), "Segment " + (hits.size() + 1)),
                        "confidence", number(segment.get("confidence"), 0.9)
                ));
            }
        }
        return hits;
    }

    private Map<String, Object> readNestedRule(Map<String, Object> segment) {
        Object rules = segment.get("rules");
        if (rules instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return segment;
    }

    private boolean matchRuleGroup(Map<String, Object> rules, Map<String, Object> profile, Map<String, Object> event) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        if (Boolean.TRUE.equals(rules.get("always"))) {
            return true;
        }
        String operator = normalize(defaultValue(asString(rules.get("operator")), "AND"));
        List<Boolean> results = new ArrayList<>();
        Object conditions = rules.get("conditions");
        if (conditions instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> condition = new LinkedHashMap<>();
                    map.forEach((key, value) -> condition.put(String.valueOf(key), value));
                    results.add(matchCondition(condition, profile, event));
                }
            }
        }
        Object groups = rules.get("groups");
        if (groups instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> group = new LinkedHashMap<>();
                    map.forEach((key, value) -> group.put(String.valueOf(key), value));
                    results.add(matchRuleGroup(group, profile, event));
                }
            }
        }
        if (results.isEmpty()) {
            return false;
        }
        return "OR".equals(operator) ? results.stream().anyMatch(Boolean::booleanValue) : results.stream().allMatch(Boolean::booleanValue);
    }

    private boolean matchCondition(Map<String, Object> condition, Map<String, Object> profile, Map<String, Object> event) {
        String field = defaultValue(asString(condition.get("field")), asString(condition.get("key")));
        String op = normalize(defaultValue(asString(condition.get("op")), defaultValue(asString(condition.get("operator")), "EQUALS")));
        Object actual = lookup(field, profile, event, asString(condition.get("source")));
        Object expected = condition.get("value");
        return switch (op) {
            case "EQUALS" -> Objects.equals(text(actual), text(expected));
            case "NOT_EQUALS" -> !Objects.equals(text(actual), text(expected));
            case "CONTAINS" -> text(actual).toLowerCase(Locale.ROOT).contains(text(expected).toLowerCase(Locale.ROOT));
            case "EXISTS" -> actual != null && !text(actual).isBlank();
            case "IN" -> expected instanceof Collection<?> collection && collection.stream().map(this::text).anyMatch(text(actual)::equals);
            case "GREATER_THAN" -> number(actual, 0) > number(expected, 0);
            case "LESS_THAN" -> number(actual, 0) < number(expected, 0);
            default -> false;
        };
    }

    private Object lookup(String field, Map<String, Object> profile, Map<String, Object> event, String source) {
        if ("event".equalsIgnoreCase(defaultValue(source, ""))) {
            return nestedValue(event, field);
        }
        if ("profile".equalsIgnoreCase(defaultValue(source, ""))) {
            return nestedValue(profile, field);
        }
        Object profileValue = nestedValue(profile, field);
        return profileValue == null ? nestedValue(event, field) : profileValue;
    }

    @SuppressWarnings("unchecked")
    private Object nestedValue(Map<String, Object> source, String path) {
        if (source == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private List<Map<String, Object>> guardrailFindings(Map<String, Object> profile, Map<String, Object> event, Map<String, Object> guardrails) {
        List<Map<String, Object>> findings = new ArrayList<>();
        String consent = text(firstObject(profile.get("consent"), profile.get("emailConsent"), event.get("consent")));
        if (Boolean.TRUE.equals(guardrails.get("consentRequired")) && !"OPT_IN".equalsIgnoreCase(consent) && !"true".equalsIgnoreCase(consent)) {
            findings.add(finding("consent.required", "BLOCK", "Personalization requires active consent."));
        }
        if (Boolean.TRUE.equals(profile.get("usesSensitiveData")) && !Boolean.TRUE.equals(guardrails.get("sensitiveDataAllowed"))) {
            findings.add(finding("sensitive_data.blocked", "BLOCK", "Sensitive-data personalization is blocked by guardrails."));
        }
        if (number(event.get("frequencyCount"), 0) > number(guardrails.get("maxFrequency"), Double.MAX_VALUE)) {
            findings.add(finding("frequency.exceeded", "BLOCK", "Frequency guardrail exceeded."));
        }
        if (findings.isEmpty()) {
            findings.add(finding("guardrails.pass", "INFO", "Realtime guardrails passed."));
        }
        return findings;
    }

    private Map<String, Object> selectVariant(List<Map<String, Object>> variants,
                                              List<Map<String, Object>> segmentHits,
                                              Map<String, Object> profile,
                                              Map<String, Object> event,
                                              List<Map<String, Object>> findings) {
        if (findings.stream().anyMatch(this::blockingFinding)) {
            return map("eligible", false, "reason", "Guardrail blocked personalization.");
        }
        List<String> hitKeys = segmentHits.stream().map(hit -> text(hit.get("key"))).toList();
        String requestedChannel = normalize(defaultValue(asString(event.get("channel")), "ANY"));
        List<Map<String, Object>> candidates = variants == null ? List.of() : variants;
        return candidates.stream()
                .filter(variant -> variantAllowed(variant, requestedChannel, hitKeys))
                .max(Comparator.comparingDouble(variant -> variantScore(variant, profile, requestedChannel)))
                .map(variant -> map(
                        "eligible", true,
                        "variantKey", defaultValue(asString(variant.get("key")), "variant"),
                        "channel", normalize(defaultValue(asString(variant.get("channel")), requestedChannel)),
                        "score", variantScore(variant, profile, requestedChannel),
                        "payload", safeMapValue(variant.get("payload"))
                ))
                .orElseGet(() -> map("eligible", false, "reason", "No eligible variant matched channel and segment guardrails."));
    }

    private boolean variantAllowed(Map<String, Object> variant, String requestedChannel, List<String> hitKeys) {
        String channel = normalize(defaultValue(asString(variant.get("channel")), "ANY"));
        if (!"ANY".equals(requestedChannel) && !"ANY".equals(channel) && !requestedChannel.equals(channel)) {
            return false;
        }
        String requiredSegment = firstText(asString(variant.get("segmentKey")), asString(variant.get("requiredSegment")));
        return requiredSegment == null || hitKeys.contains(requiredSegment);
    }

    private double variantScore(Map<String, Object> variant, Map<String, Object> profile, String requestedChannel) {
        double score = number(variant.get("weight"), 1);
        if (Objects.equals(text(variant.get("tag")).toLowerCase(Locale.ROOT), text(profile.get("interest")).toLowerCase(Locale.ROOT))) {
            score += 25;
        }
        if (requestedChannel.equals(normalize(asString(variant.get("channel"))))) {
            score += 10;
        }
        return score + number(variant.get("scoreBoost"), 0);
    }

    private Map<String, Object> personalizationPayload(Map<String, Object> defaults,
                                                       Map<String, Object> variantDecision,
                                                       List<Map<String, Object>> segmentHits,
                                                       Map<String, Object> profile) {
        Map<String, Object> payload = new LinkedHashMap<>(safeMap(defaults));
        payload.put("segments", segmentHits.stream().map(hit -> hit.get("key")).toList());
        payload.put("lifecycleStage", profile.getOrDefault("lifecycleStage", "UNKNOWN"));
        payload.put("variant", variantDecision.get("variantKey"));
        payload.put("variantPayload", variantDecision.getOrDefault("payload", Map.of()));
        payload.put("resolvedAt", Instant.now().toString());
        return payload;
    }
}
