package com.legent.foundation.service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.WorkflowBenchmarkRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowBenchmarkService extends PerformanceLedgerSupport {

    public WorkflowBenchmarkService(CorePlatformRepository repository, ObjectMapper objectMapper) {
        super(repository, objectMapper);
    }

    @Transactional
    public Map<String, Object> record(WorkflowBenchmarkRequest request) {
        int creation = valueOr(request.getCampaignCreationSeconds(), 0);
        int errors = valueOr(request.getLaunchErrors(), 0);
        int observability = valueOr(request.getObservabilityScore(), 0);
        int competitorCreation = valueOr(request.getCompetitorCreationSeconds(), 0);
        int competitorErrors = valueOr(request.getCompetitorLaunchErrors(), 0);
        int competitorObservability = valueOr(request.getCompetitorObservabilityScore(), 0);
        Map<String, Object> deltas = map(
                "campaignCreationSeconds", competitorCreation - creation,
                "launchErrors", competitorErrors - errors,
                "observabilityScore", observability - competitorObservability
        );
        String verdict = (competitorCreation > creation && competitorErrors > errors && observability > competitorObservability)
                ? "LEADER"
                : (competitorCreation >= creation && competitorErrors >= errors ? "PARITY" : "WATCH");

        Map<String, Object> values = baseValues(workspace(request.getWorkspaceId()));
        values.put("benchmark_key", request.getBenchmarkKey().trim());
        values.put("flow_name", request.getFlowName().trim());
        values.put("competitor", defaultValue(request.getCompetitor(), "Salesforce MCE"));
        values.put("campaign_creation_seconds", creation);
        values.put("launch_errors", errors);
        values.put("observability_score", observability);
        values.put("competitor_creation_seconds", competitorCreation);
        values.put("competitor_launch_errors", competitorErrors);
        values.put("competitor_observability_score", competitorObservability);
        values.put("verdict", verdict);
        values.put("deltas", toJson(deltas));
        values.put("evidence", toJson(request.getEvidence()));
        Map<String, Object> saved = repository.insert("workflow_benchmark_runs", values, List.of("deltas", "evidence"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.get("id"));
        result.put("benchmarkKey", request.getBenchmarkKey());
        result.put("flowName", request.getFlowName());
        result.put("competitor", values.get("competitor"));
        result.put("verdict", verdict);
        result.put("deltas", deltas);
        result.put("campaignCreationSeconds", creation);
        result.put("launchErrors", errors);
        result.put("observabilityScore", observability);
        result.put("recordedAt", Instant.now().toString());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBenchmarks(String workspaceId, int limit) {
        return listLatest("workflow_benchmark_runs", workspace(workspaceId), clamp(limit, 1, 500));
    }
}
