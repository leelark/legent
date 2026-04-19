package com.legent.automation.controller;

import com.legent.automation.domain.WorkflowDefinition;
import com.legent.automation.repository.WorkflowDefinitionRepository;
import com.legent.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/workflow-definitions")
@RequiredArgsConstructor
public class WorkflowDefinitionController {
    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    @GetMapping("/{workflowId}/latest")
    public ApiResponse<WorkflowDefinition> getLatestDefinition(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String workflowId
    ) {
        Optional<WorkflowDefinition> latest = workflowDefinitionRepository.findAll().stream()
                .filter(wd -> wd.getWorkflowId().equals(workflowId) && wd.getTenantId().equals(tenantId))
                .max((a, b) -> Integer.compare(a.getVersion(), b.getVersion()));
        return latest.map(ApiResponse::ok)
            .orElseGet(() -> ApiResponse.error("NOT_FOUND", "No definition found", null));
    }

    @PostMapping
    public ApiResponse<WorkflowDefinition> saveDefinition(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody WorkflowDefinition definition
    ) {
        definition.setTenantId(tenantId);
        WorkflowDefinition saved = workflowDefinitionRepository.save(definition);
        return ApiResponse.ok(saved);
    }
}
