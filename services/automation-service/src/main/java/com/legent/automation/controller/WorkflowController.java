package com.legent.automation.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.automation.domain.Workflow;
import com.legent.automation.repository.WorkflowRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowRepository workflowRepository;

    @GetMapping
    public ApiResponse<List<Workflow>> listWorkflows() {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(workflowRepository.findByTenantId(tenantId));
    }

    @PostMapping
    public ApiResponse<Workflow> createWorkflow(@RequestBody Workflow workflow) {
        String tenantId = TenantContext.requireTenantId();
        workflow.setTenantId(tenantId);
        return ApiResponse.ok(workflowRepository.save(workflow));
    }
    
    // Note: Versioning and Definition Upserts would normally reside here. 
    // Omitting for brevity, as the Engine is the core focus of this module layout validation.
}
