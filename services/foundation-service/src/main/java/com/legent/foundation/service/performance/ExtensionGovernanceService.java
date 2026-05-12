package com.legent.foundation.service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.ExtensionPackageRequest;
import com.legent.foundation.dto.performance.ExtensionValidationRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExtensionGovernanceService extends PerformanceLedgerSupport {

    private static final List<String> FORBIDDEN_SCRIPT_TOKENS = List.of(
            "runtime.exec",
            "processbuilder",
            "child_process",
            "eval(",
            "function(",
            "rm -rf",
            "powershell",
            "cmd.exe",
            "curl ",
            "wget ",
            "secret",
            "private_key"
    );

    public ExtensionGovernanceService(CorePlatformRepository repository, ObjectMapper objectMapper) {
        super(repository, objectMapper);
    }

    @Transactional
    public Map<String, Object> upsertPackage(ExtensionPackageRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> values = baseValues(workspaceId);
        values.put("package_key", request.getPackageKey().trim());
        values.put("display_name", request.getDisplayName().trim());
        values.put("package_type", normalize(defaultValue(request.getPackageType(), "HYBRID")));
        values.put("status", normalize(defaultValue(request.getStatus(), "DRAFT")));
        values.put("scopes", toJson(request.getScopes() == null ? List.of() : request.getScopes()));
        values.put("manifest", toJson(request.getManifest()));
        values.put("scripts", toJson(request.getScripts() == null ? List.of() : request.getScripts()));
        values.put("test_requirements", toJson(request.getTestRequirements() == null ? List.of() : request.getTestRequirements()));
        values.put("approval_status", normalize(defaultValue(request.getApprovalStatus(), "PENDING")));
        values.put("metadata", toJson(request.getMetadata()));
        return upsertByKey("extension_governance_packages", "package_key", request.getPackageKey().trim(), workspaceId, values,
                List.of("scopes", "manifest", "scripts", "test_requirements", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPackages(String workspaceId) {
        return listScoped("extension_governance_packages", workspace(workspaceId), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> validatePackage(String packageId, ExtensionValidationRequest request) {
        Map<String, Object> pkg = requireById("extension_governance_packages", packageId);
        List<Map<String, Object>> findings = new ArrayList<>();
        List<String> missingChecks = new ArrayList<>();
        List<String> passedChecks = new ArrayList<>();
        List<String> forbiddenTokens = new ArrayList<>();
        Map<String, Object> manifest = readMap(pkg.get("manifest"));
        List<Map<String, Object>> scripts = readMapList(pkg.get("scripts"));
        List<String> scopes = readStringList(pkg.get("scopes"));
        List<String> requirements = readStringList(pkg.get("test_requirements"));

        requireManifest(manifest, missingChecks, findings);
        validateScopes(scopes, asString(pkg.get("approval_status")), findings, passedChecks);
        validateScripts(scripts, forbiddenTokens, findings, passedChecks);
        if (requirements.isEmpty()) {
            missingChecks.add("test_requirements");
            findings.add(finding("tests.missing", "BLOCK", "At least one test requirement is required."));
        } else {
            passedChecks.add("test_requirements");
        }

        boolean failed = findings.stream().anyMatch(this::blockingFinding);
        String status = failed ? "FAILED" : "PASSED";
        Map<String, Object> values = baseValues(asString(pkg.get("workspace_id")));
        values.put("package_id", packageId);
        values.put("status", status);
        values.put("findings", toJson(findings));
        values.put("forbidden_tokens", toJson(forbiddenTokens));
        values.put("passed_checks", toJson(passedChecks));
        values.put("missing_checks", toJson(missingChecks));
        values.put("evidence", toJson(request == null ? null : request.getEvidence()));
        Map<String, Object> saved = repository.insert("extension_governance_test_runs", values,
                List.of("findings", "forbidden_tokens", "passed_checks", "missing_checks", "evidence"));

        Map<String, Object> response = map(
                "id", saved.get("id"),
                "packageId", packageId,
                "status", status,
                "findings", findings,
                "forbiddenTokens", forbiddenTokens,
                "passedChecks", passedChecks,
                "missingChecks", missingChecks
        );
        return response;
    }

    private void requireManifest(Map<String, Object> manifest, List<String> missingChecks, List<Map<String, Object>> findings) {
        for (String required : List.of("name", "version", "entrypoint")) {
            if (blankToNull(asString(manifest.get(required))) == null) {
                missingChecks.add("manifest." + required);
                findings.add(finding("manifest." + required, "BLOCK", "Manifest requires " + required + "."));
            }
        }
    }

    private void validateScopes(List<String> scopes, String approvalStatus, List<Map<String, Object>> findings, List<String> passedChecks) {
        boolean unsafe = scopes.stream().map(this::normalize).anyMatch(scope -> scope.equals("*") || scope.equals("TENANT:*") || scope.equals("ADMIN:*"));
        if (unsafe && !"APPROVED".equalsIgnoreCase(defaultValue(approvalStatus, ""))) {
            findings.add(finding("scopes.approval", "BLOCK", "Unsafe scopes require approved governance status."));
        } else {
            passedChecks.add("scope_governance");
        }
    }

    private void validateScripts(List<Map<String, Object>> scripts,
                                 List<String> forbiddenTokens,
                                 List<Map<String, Object>> findings,
                                 List<String> passedChecks) {
        for (Map<String, Object> script : scripts) {
            String source = text(firstObject(script.get("source"), script.get("body"), script.get("code")));
            String lower = source.toLowerCase(Locale.ROOT);
            for (String token : FORBIDDEN_SCRIPT_TOKENS) {
                if (lower.contains(token)) {
                    forbiddenTokens.add(token);
                }
            }
        }
        if (!forbiddenTokens.isEmpty()) {
            findings.add(finding("scripts.forbidden_tokens", "BLOCK", "Script source contains forbidden runtime or secret-access tokens."));
        } else {
            passedChecks.add("static_script_scan");
        }
    }
}
