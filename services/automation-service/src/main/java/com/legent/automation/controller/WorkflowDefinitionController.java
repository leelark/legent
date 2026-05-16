package com.legent.automation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.domain.WorkflowDefinition;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.automation.service.WorkflowStudioService;
import com.legent.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflow-definitions")
@RequiredArgsConstructor
public class WorkflowDefinitionController {
    private final WorkflowStudioService workflowStudioService;
    private final ObjectMapper objectMapper;

    @GetMapping("/{workflowId}/latest")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:read', principal.roles)")
    public ApiResponse<WorkflowDefinition> getLatestDefinition(@PathVariable String workflowId) {
        return ApiResponse.ok(workflowStudioService.getLatestDefinition(workflowId));
    }

    @PostMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:write', principal.roles)")
    public ApiResponse<WorkflowDefinition> saveDefinition(@RequestBody Map<String, Object> request) {
        String workflowId = asString(request.get("workflowId"));
        if (workflowId == null) {
            return ApiResponse.error("INVALID_REQUEST", "workflowId is required", null);
        }

        Integer version = asInteger(request.get("version"));
        boolean published = Boolean.TRUE.equals(request.get("published"));

        WorkflowGraphDto graph = null;
        Object definition = request.get("definition");
        if (definition instanceof String json && !json.isBlank()) {
            try {
                graph = objectMapper.readValue(json, WorkflowGraphDto.class);
            } catch (Exception e) {
                return ApiResponse.error("INVALID_GRAPH", "definition JSON is invalid", e.getMessage());
            }
        } else if (definition != null) {
            graph = objectMapper.convertValue(definition, WorkflowGraphDto.class);
        } else if (request.get("graph") != null) {
            graph = objectMapper.convertValue(request.get("graph"), WorkflowGraphDto.class);
        } else {
            graph = objectMapper.convertValue(request, WorkflowGraphDto.class);
        }

        return ApiResponse.ok(workflowStudioService.saveDefinition(workflowId, version, graph, published));
    }

    @GetMapping("/{workflowId}/versions/{version}")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:read', principal.roles)")
    public ApiResponse<WorkflowDefinition> getDefinitionVersion(@PathVariable String workflowId, @PathVariable Integer version) {
        return ApiResponse.ok(workflowStudioService.getDefinitionVersion(workflowId, version));
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
