package com.legent.automation.controller;

import com.legent.automation.domain.WorkflowDefinition;
import com.legent.automation.repository.WorkflowDefinitionRepository;
import com.legent.common.dto.ApiResponse;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/workflow-definitions")
@RequiredArgsConstructor
public class WorkflowDefinitionController {
    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    @GetMapping("/{workflowId}/latest")
    public ApiResponse<WorkflowDefinition> getLatestDefinition(@PathVariable String workflowId) {
        String tenantId = TenantContext.getTenantId();
        Optional<WorkflowDefinition> latest = workflowDefinitionRepository.findTopByTenantIdAndWorkflowIdOrderByVersionDesc(tenantId, workflowId);
        return latest.map(ApiResponse::ok)
            .orElseGet(() -> ApiResponse.error("NOT_FOUND", "No definition found", null));
    }

    @PostMapping
    public ApiResponse<WorkflowDefinition> saveDefinition(@RequestBody WorkflowDefinition definition) {
        String tenantId = TenantContext.getTenantId();
        definition.setTenantId(tenantId);
        WorkflowDefinition saved = workflowDefinitionRepository.save(definition);
        return ApiResponse.ok(saved);
    }
}
