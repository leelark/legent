package com.legent.foundation.service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.OperationsAssistRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OperationsAssistanceService extends PerformanceLedgerSupport {

    private static final List<String> OPERATION_TYPES = List.of("BUILD", "QA", "LAUNCH", "MONITOR", "INCIDENT");
    private static final Set<String> ALLOWED_TELEMETRY_FIELDS = Set.of(
            "riskScore",
            "successRatePercent",
            "saturationPercent",
            "p95LatencyMs",
            "errors",
            "blockers"
    );
    private static final List<String> SENSITIVE_TELEMETRY_KEY_TOKENS = List.of(
            "secret",
            "token",
            "password",
            "credential",
            "privatekey",
            "apikey",
            "authorization",
            "bearer",
            "cookie",
            "session",
            "jwt",
            "oauth",
            "clientsecret",
            "accesskey",
            "recipientemail",
            "subscriberemail",
            "customeremail",
            "emailaddress",
            "phone",
            "ssn"
    );

    public OperationsAssistanceService(CorePlatformRepository repository, ObjectMapper objectMapper) {
        super(repository, objectMapper);
    }

    @Transactional
    public Map<String, Object> assist(OperationsAssistRequest request) {
        String operationType = normalize(request.getOperationType());
        if (!OPERATION_TYPES.contains(operationType)) {
            throw new IllegalArgumentException("operationType must be one of " + OPERATION_TYPES);
        }
        String workspaceId = requireWorkspace(request.getWorkspaceId());
        Map<String, Object> telemetry = minimizedTelemetry(request.getTelemetry());
        String severity = severity(operationType, telemetry);
        int risk = opsRisk(operationType, telemetry, severity);
        List<Map<String, Object>> checklist = opsChecklist(operationType, severity);
        List<Map<String, Object>> actions = opsActions(operationType, telemetry, severity);
        boolean approvalRequired = risk >= 50 || "P1".equals(severity) || "LAUNCH".equals(operationType);

        Map<String, Object> values = baseValues(workspaceId);
        values.put("operation_type", operationType);
        values.put("artifact_type", blankToNull(request.getArtifactType()));
        values.put("artifact_id", blankToNull(request.getArtifactId()));
        values.put("severity", severity);
        values.put("status", approvalRequired ? "PENDING_APPROVAL" : "READY");
        values.put("risk_score", risk);
        values.put("telemetry", toJson(telemetry));
        values.put("checklist", toJson(checklist));
        values.put("recommended_actions", toJson(actions));
        values.put("evidence_refs", toJson(request.getEvidenceRefs() == null ? List.of() : request.getEvidenceRefs()));
        values.put("approval_required", approvalRequired);
        Map<String, Object> saved = repository.insert("operations_assistance_reviews", values,
                List.of("telemetry", "checklist", "recommended_actions", "evidence_refs"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.get("id"));
        response.put("operationType", operationType);
        response.put("severity", severity);
        response.put("status", values.get("status"));
        response.put("riskScore", risk);
        response.put("checklist", checklist);
        response.put("recommendedActions", actions);
        response.put("evidenceRefs", request.getEvidenceRefs() == null ? List.of() : request.getEvidenceRefs());
        response.put("approvalRequired", approvalRequired);
        response.put("reviewedAt", Instant.now().toString());
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listReviews(String workspaceId, int limit) {
        return listLatest("operations_assistance_reviews", requireWorkspace(workspaceId), clamp(limit, 1, 500));
    }

    private String requireWorkspace(String workspaceId) {
        tenant();
        String resolved = workspace(workspaceId);
        if (resolved == null) {
            throw new IllegalArgumentException("workspaceId is required for operations assistance.");
        }
        return resolved;
    }

    private Map<String, Object> minimizedTelemetry(Map<String, Object> telemetry) {
        Map<String, Object> source = safeMap(telemetry);
        rejectSensitiveTelemetryKeys(source, "telemetry");
        if (source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> minimized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            Object safeValue = operationalTelemetryValue(value);
            if (ALLOWED_TELEMETRY_FIELDS.contains(key) && safeValue != null) {
                minimized.put(key, safeValue);
            }
        });
        return minimized;
    }

    private void rejectSensitiveTelemetryKeys(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                String keyText = String.valueOf(key);
                String childPath = path + "." + keyText;
                if (sensitiveTelemetryKey(keyText)) {
                    throw new IllegalArgumentException("operations assistance telemetry contains sensitive key: " + childPath);
                }
                rejectSensitiveTelemetryKeys(item, childPath);
            });
            return;
        }
        if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                rejectSensitiveTelemetryKeys(item, path + "[" + index + "]");
                index++;
            }
        }
    }

    private boolean sensitiveTelemetryKey(String key) {
        String normalized = key == null ? "" : key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_TELEMETRY_KEY_TOKENS.stream().anyMatch(normalized::contains);
    }

    private Object operationalTelemetryValue(Object value) {
        if (value instanceof Double number && !Double.isFinite(number)) {
            return null;
        }
        if (value instanceof Float number && !Float.isFinite(number)) {
            return null;
        }
        if (value instanceof Number) {
            return value;
        }
        if (value instanceof CharSequence text) {
            String trimmed = text.toString().trim();
            if (trimmed.isBlank()) {
                return null;
            }
            try {
                double parsed = Double.parseDouble(trimmed);
                return Double.isFinite(parsed) ? trimmed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String severity(String operationType, Map<String, Object> telemetry) {
        if (!"INCIDENT".equals(operationType)) {
            return number(telemetry.get("riskScore"), 0) >= 70 ? "P2" : "P3";
        }
        double success = number(telemetry.get("successRatePercent"), 100);
        double saturation = number(telemetry.get("saturationPercent"), 0);
        long p95 = Math.round(number(telemetry.get("p95LatencyMs"), 0));
        long errors = Math.round(number(telemetry.get("errors"), 0));
        if (success < 95 || saturation >= 95 || errors >= 1000) {
            return "P1";
        }
        if (success < 99 || saturation >= 85 || p95 >= 2000 || errors > 0) {
            return "P2";
        }
        return "P3";
    }

    private int opsRisk(String operationType, Map<String, Object> telemetry, String severity) {
        int risk = (int) number(telemetry.get("riskScore"), 0);
        risk += switch (operationType) {
            case "LAUNCH" -> 20;
            case "INCIDENT" -> "P1".equals(severity) ? 80 : "P2".equals(severity) ? 55 : 20;
            case "QA" -> 10;
            case "MONITOR" -> 5;
            default -> 0;
        };
        if (number(telemetry.get("blockers"), 0) > 0) {
            risk += 30;
        }
        if (number(telemetry.get("saturationPercent"), 0) >= 90) {
            risk += 20;
        }
        return clamp(risk, 0, 100);
    }

    private List<Map<String, Object>> opsChecklist(String operationType, String severity) {
        List<Map<String, Object>> checklist = new ArrayList<>();
        checklist.add(rec("context", "INFO", "Confirm tenant, workspace, artifact, and current release state."));
        checklist.add(rec("policy", "INFO", "Run consent, brand, frequency, deliverability, and rollback checks."));
        if ("LAUNCH".equals(operationType)) {
            checklist.add(rec("launch", "HIGH", "Verify readiness blockers are zero before launch confirmation."));
        }
        if ("QA".equals(operationType)) {
            checklist.add(rec("qa", "HIGH", "Run targeted unit, API, and E2E checks for changed workflows."));
        }
        if ("INCIDENT".equals(operationType)) {
            checklist.add(rec("incident", "P1".equals(severity) ? "CRITICAL" : "HIGH", "Open incident, freeze risky deploys, and attach telemetry evidence."));
        }
        return checklist;
    }

    private List<Map<String, Object>> opsActions(String operationType, Map<String, Object> telemetry, String severity) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if ("INCIDENT".equals(operationType)) {
            actions.add(rec("incident.route", severity, "Route to on-call owner with error budget and saturation snapshot."));
            if (number(telemetry.get("saturationPercent"), 0) >= 85) {
                actions.add(rec("capacity.scale", "HIGH", "Scale workers or pause low-priority tenants until saturation recovers."));
            }
            if (number(telemetry.get("p95LatencyMs"), 0) >= 2000) {
                actions.add(rec("latency.playbook", "HIGH", "Run latency playbook and inspect downstream dependencies."));
            }
            return actions;
        }
        actions.add(rec(operationType.toLowerCase(Locale.ROOT) + ".review", "MEDIUM", "Use generated checklist as preflight evidence."));
        if ("LAUNCH".equals(operationType)) {
            actions.add(rec("launch.safe_fix", "HIGH", "Apply safe fixes only for tracking, compliance, timezone, and metadata defaults."));
        }
        return actions;
    }
}
