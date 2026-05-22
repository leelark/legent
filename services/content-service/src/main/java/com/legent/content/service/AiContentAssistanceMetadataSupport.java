package com.legent.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.dto.TemplateWorkflowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiContentAssistanceMetadataSupport {

    private static final String METADATA_KEY = "aiContentAssistance";
    private static final String APPLIED_STATUS = "APPLIED_TO_DRAFT";
    private static final String APPROVED_DECISION = "APPROVED_DRAFT_ONLY";
    private static final String APPLY_TO_DRAFT_ACTION = "APPLY_TO_DRAFT";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String applyApprovedDraftEvidence(String metadataJson,
                                             TemplateWorkflowDto.AiDraftApplication application,
                                             String actor) {
        if (application == null) {
            return metadataJson;
        }
        requireApprovedDraftEvidence(application);
        Map<String, Object> metadata = readMetadata(metadataJson);
        Map<String, Object> aiMetadata = new LinkedHashMap<>();
        aiMetadata.put("status", APPLIED_STATUS);
        aiMetadata.put("decision", normalize(application.getDecision()));
        aiMetadata.put("requestedAction", normalizedAction(application.getRequestedAction()));
        aiMetadata.put("auditId", application.getAuditId().trim());
        aiMetadata.put("policyKey", application.getPolicyKey().trim());
        aiMetadata.put("policyVersion", application.getPolicyVersion().trim());
        if (hasText(application.getPromptHash())) {
            aiMetadata.put("promptHash", application.getPromptHash().trim().toLowerCase(Locale.ROOT));
        }
        aiMetadata.put("outputHash", application.getOutputHash().trim().toLowerCase(Locale.ROOT));
        aiMetadata.put("evidenceRefs", application.getEvidenceRefs().stream()
                .filter(this::hasText)
                .map(String::trim)
                .toList());
        aiMetadata.put("humanReviewed", true);
        aiMetadata.put("appliedBy", hasText(actor) ? actor.trim() : "system");
        aiMetadata.put("appliedAt", Instant.now().toString());
        metadata.put(METADATA_KEY, aiMetadata);
        return writeMetadata(metadata);
    }

    public void requireResolvedForOperation(EmailTemplate template, String operation) {
        Map<String, Object> aiMetadata = aiMetadata(template);
        if (aiMetadata.isEmpty()) {
            return;
        }
        String status = normalize(asString(aiMetadata.get("status")));
        String decision = normalize(asString(aiMetadata.get("decision")));
        boolean humanReviewed = booleanValue(aiMetadata.get("humanReviewed"));
        String auditId = asString(aiMetadata.get("auditId"));
        String outputHash = asString(aiMetadata.get("outputHash"));
        if (APPLIED_STATUS.equals(status)
                && APPROVED_DECISION.equals(decision)
                && humanReviewed
                && hasText(auditId)
                && validSha256(outputHash)) {
            return;
        }
        throw new ValidationException(
                "aiContentAssistance",
                "Template cannot " + operation + " until AI content assistance evidence is approved, human reviewed, and hash-backed");
    }

    private void requireApprovedDraftEvidence(TemplateWorkflowDto.AiDraftApplication application) {
        requireEquals("decision", APPROVED_DECISION, application.getDecision());
        requireEquals("requestedAction", APPLY_TO_DRAFT_ACTION, normalizedAction(application.getRequestedAction()));
        requirePresent("auditId", application.getAuditId());
        requirePresent("policyKey", application.getPolicyKey());
        requirePresent("policyVersion", application.getPolicyVersion());
        if (!Boolean.TRUE.equals(application.getHumanReviewed())) {
            throw new ValidationException("aiAssistance.humanReviewed", "AI draft application requires human review approval");
        }
        if (!validSha256(application.getOutputHash())) {
            throw new ValidationException("aiAssistance.outputHash", "AI draft application requires a SHA-256 output hash");
        }
        if (hasText(application.getPromptHash()) && !validSha256(application.getPromptHash())) {
            throw new ValidationException("aiAssistance.promptHash", "AI draft application prompt hash must be SHA-256 when present");
        }
        if (application.getEvidenceRefs() == null
                || application.getEvidenceRefs().stream().noneMatch(this::hasText)) {
            throw new ValidationException("aiAssistance.evidenceRefs", "AI draft application requires at least one evidence reference");
        }
    }

    private Map<String, Object> aiMetadata(EmailTemplate template) {
        Map<String, Object> metadata = readMetadata(template == null ? null : template.getMetadata());
        Object value = metadata.get(METADATA_KEY);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (key != null) {
                    normalized.put(String.valueOf(key), item);
                }
            });
            return normalized;
        }
        throw new ValidationException("aiContentAssistance", "AI content assistance metadata must be an object");
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, MAP_TYPE);
            if (metadata == null) {
                throw new ValidationException("metadata", "Template metadata must be a JSON object before AI content assistance evidence can be evaluated");
            }
            return new LinkedHashMap<>(metadata);
        } catch (JsonProcessingException e) {
            throw new ValidationException("metadata", "Template metadata must be a JSON object before AI content assistance evidence can be evaluated");
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize template metadata", e);
        }
    }

    private void requireEquals(String field, String expected, String actual) {
        if (!expected.equals(normalize(actual))) {
            throw new ValidationException("aiAssistance." + field, "AI draft application requires " + expected);
        }
    }

    private void requirePresent(String field, String value) {
        if (!hasText(value)) {
            throw new ValidationException("aiAssistance." + field, "AI draft application requires " + field);
        }
    }

    private String normalizedAction(String value) {
        return hasText(value) ? normalize(value) : APPLY_TO_DRAFT_ACTION;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean validSha256(String value) {
        return hasText(value) && value.trim().matches("(?i)[a-f0-9]{64}");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
