package com.legent.foundation.service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.repository.CorePlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PerformanceIntelligenceSummaryService extends PerformanceLedgerSupport {

    public PerformanceIntelligenceSummaryService(CorePlatformRepository repository, ObjectMapper objectMapper) {
        super(repository, objectMapper);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(String workspaceId) {
        String resolvedWorkspace = workspace(workspaceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("personalizationEvaluations", listLatest("personalization_evaluation_runs", resolvedWorkspace, 10));
        result.put("optimizationPolicies", listScoped("performance_optimization_policies", resolvedWorkspace, "created_at DESC"));
        result.put("optimizationRuns", listLatest("performance_optimization_runs", resolvedWorkspace, 10));
        result.put("extensionPackages", listScoped("extension_governance_packages", resolvedWorkspace, "created_at DESC"));
        result.put("extensionValidationRuns", listLatest("extension_governance_test_runs", resolvedWorkspace, 10));
        result.put("operationsReviews", listLatest("operations_assistance_reviews", resolvedWorkspace, 10));
        result.put("workflowBenchmarks", listLatest("workflow_benchmark_runs", resolvedWorkspace, 10));
        result.put("generatedAt", Instant.now().toString());
        return result;
    }
}
