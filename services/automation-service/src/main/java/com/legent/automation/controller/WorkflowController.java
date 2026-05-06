package com.legent.automation.controller;

import com.legent.automation.domain.InstanceHistory;
import com.legent.automation.domain.Workflow;
import com.legent.automation.domain.WorkflowDefinition;
import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.domain.WorkflowSchedule;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.automation.service.WorkflowScheduleService;
import com.legent.automation.service.WorkflowStudioService;
import com.legent.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowStudioService workflowStudioService;
    private final WorkflowScheduleService workflowScheduleService;

    @GetMapping
    public ApiResponse<List<Workflow>> listWorkflows() {
        return ApiResponse.ok(workflowStudioService.listWorkflows());
    }

    @PostMapping
    public ApiResponse<Workflow> createWorkflow(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(workflowStudioService.createWorkflow(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Workflow> getWorkflow(@PathVariable String id) {
        return ApiResponse.ok(workflowStudioService.getWorkflow(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Workflow> updateWorkflow(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(workflowStudioService.updateWorkflow(id, request));
    }

    @PostMapping("/{id}/validate")
    public ApiResponse<Map<String, Object>> validateWorkflow(@PathVariable String id, @RequestBody WorkflowGraphDto graph) {
        return ApiResponse.ok(workflowStudioService.validateGraph(graph));
    }

    @PostMapping("/{id}/definitions")
    public ApiResponse<WorkflowDefinition> saveWorkflowDefinition(@PathVariable String id,
                                                                  @RequestBody WorkflowGraphDto graph,
                                                                  @RequestParam(required = false) Integer version,
                                                                  @RequestParam(defaultValue = "false") boolean published) {
        return ApiResponse.ok(workflowStudioService.saveDefinition(id, version, graph, published));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Workflow> publishWorkflow(@PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        Integer version = request == null ? null : asInteger(request.get("version"));
        return ApiResponse.ok(workflowStudioService.publish(id, version));
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<Workflow> pauseWorkflow(@PathVariable String id) {
        return ApiResponse.ok(workflowStudioService.pause(id));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<Workflow> resumeWorkflow(@PathVariable String id) {
        return ApiResponse.ok(workflowStudioService.resume(id));
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<Workflow> stopWorkflow(@PathVariable String id) {
        return ApiResponse.ok(workflowStudioService.stop(id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Workflow> archiveWorkflow(@PathVariable String id) {
        return ApiResponse.ok(workflowStudioService.archive(id));
    }

    @PostMapping("/{id}/rollback")
    public ApiResponse<Workflow> rollbackWorkflow(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(workflowStudioService.rollback(id, asInteger(request.get("version"))));
    }

    @PostMapping("/{id}/clone")
    public ApiResponse<Workflow> cloneWorkflow(@PathVariable String id) {
        return ApiResponse.ok(workflowStudioService.cloneWorkflow(id));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<WorkflowDefinition>> listWorkflowVersions(@PathVariable String id) {
        return ApiResponse.ok(workflowStudioService.listVersions(id));
    }

    @GetMapping("/{id}/versions/{version}")
    public ApiResponse<WorkflowDefinition> getWorkflowVersion(@PathVariable String id, @PathVariable Integer version) {
        return ApiResponse.ok(workflowStudioService.getDefinitionVersion(id, version));
    }

    @PostMapping("/{id}/compare")
    public ApiResponse<Map<String, Object>> compareVersions(@PathVariable String id, @RequestBody Map<String, Object> request) {
        Integer leftVersion = asInteger(request.get("leftVersion"));
        Integer rightVersion = asInteger(request.get("rightVersion"));
        return ApiResponse.ok(workflowStudioService.compareVersions(id, leftVersion, rightVersion));
    }

    @PostMapping("/{id}/trigger")
    public ApiResponse<Map<String, Object>> triggerWorkflow(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(workflowStudioService.triggerWorkflow(id, request));
    }

    @GetMapping("/{id}/runs")
    public ApiResponse<List<WorkflowInstance>> listWorkflowRuns(@PathVariable String id) {
        return ApiResponse.ok(workflowStudioService.listRuns(id));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<WorkflowInstance> getRun(@PathVariable String runId) {
        return ApiResponse.ok(workflowStudioService.getRun(runId));
    }

    @GetMapping("/runs/{runId}/steps")
    public ApiResponse<List<InstanceHistory>> getRunSteps(@PathVariable String runId) {
        return ApiResponse.ok(workflowStudioService.getRunSteps(runId));
    }

    @GetMapping("/runs/{runId}/trace")
    public ApiResponse<Map<String, Object>> getRunTrace(@PathVariable String runId) {
        return ApiResponse.ok(workflowStudioService.getRunTrace(runId));
    }

    @PostMapping("/{id}/simulate")
    public ApiResponse<Map<String, Object>> simulate(@PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        WorkflowGraphDto graph = request == null ? null : coerceGraph(request.get("graph"));
        @SuppressWarnings("unchecked")
        Map<String, Object> context = request != null && request.get("context") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return ApiResponse.ok(workflowStudioService.simulate(id, graph, context, false));
    }

    @PostMapping("/{id}/dry-run")
    public ApiResponse<Map<String, Object>> dryRun(@PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        WorkflowGraphDto graph = request == null ? null : coerceGraph(request.get("graph"));
        @SuppressWarnings("unchecked")
        Map<String, Object> context = request != null && request.get("context") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return ApiResponse.ok(workflowStudioService.simulate(id, graph, context, true));
    }

    @GetMapping("/{id}/schedules")
    public ApiResponse<List<WorkflowSchedule>> listSchedules(@PathVariable String id) {
        return ApiResponse.ok(workflowScheduleService.listSchedules(id));
    }

    @PostMapping("/{id}/schedules")
    public ApiResponse<WorkflowSchedule> createSchedule(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(workflowScheduleService.createSchedule(id, request));
    }

    @PutMapping("/{id}/schedules/{scheduleId}")
    public ApiResponse<WorkflowSchedule> updateSchedule(@PathVariable String id,
                                                        @PathVariable String scheduleId,
                                                        @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(workflowScheduleService.updateSchedule(id, scheduleId, request));
    }

    @DeleteMapping("/{id}/schedules/{scheduleId}")
    public ApiResponse<Map<String, Object>> deleteSchedule(@PathVariable String id, @PathVariable String scheduleId) {
        workflowScheduleService.deleteSchedule(id, scheduleId);
        return ApiResponse.ok(Map.of("deleted", true));
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

    private WorkflowGraphDto coerceGraph(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof WorkflowGraphDto dto) {
            return dto;
        }
        return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(raw, WorkflowGraphDto.class);
    }
}
