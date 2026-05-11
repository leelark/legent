package com.legent.automation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.domain.InstanceHistory;
import com.legent.automation.domain.Workflow;
import com.legent.automation.domain.WorkflowDefinition;
import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.automation.repository.InstanceHistoryRepository;
import com.legent.automation.repository.WorkflowDefinitionRepository;
import com.legent.automation.repository.WorkflowInstanceRepository;
import com.legent.automation.repository.WorkflowRepository;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.common.constant.AppConstants;
import com.legent.common.util.IdGenerator;
import com.legent.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowStudioService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "DRAFT", Set.of("ACTIVE", "PAUSED", "ARCHIVED", "SCHEDULED"),
            "ACTIVE", Set.of("PAUSED", "STOPPED", "ARCHIVED", "FAILED"),
            "PAUSED", Set.of("ACTIVE", "STOPPED", "ARCHIVED"),
            "SCHEDULED", Set.of("ACTIVE", "PAUSED", "STOPPED", "ARCHIVED"),
            "STOPPED", Set.of("ACTIVE", "ARCHIVED"),
            "FAILED", Set.of("DRAFT", "ACTIVE", "ARCHIVED", "ROLLED_BACK"),
            "ROLLED_BACK", Set.of("ACTIVE", "PAUSED", "ARCHIVED"),
            "ARCHIVED", Set.of("DRAFT")
    );

    private final WorkflowRepository workflowRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final InstanceHistoryRepository instanceHistoryRepository;
    private final WorkflowGraphValidator workflowGraphValidator;
    private final WorkflowEventPublisher workflowEventPublisher;
    private final ObjectMapper objectMapper;

    public List<Workflow> listWorkflows() {
        return workflowRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(requireTenant(), requireWorkspace());
    }

    public Workflow getWorkflow(String workflowId) {
        return findWorkflow(workflowId);
    }

    @Transactional
    public Workflow createWorkflow(Map<String, Object> request) {
        Workflow workflow = new Workflow();
        workflow.setTenantId(requireTenant());
        workflow.setWorkspaceId(requireWorkspace());
        workflow.setTeamId(asString(request.get("teamId")));
        workflow.setOwnershipScope(defaultString(asString(request.get("ownershipScope")), "WORKSPACE"));
        workflow.setName(requiredValue(asString(request.get("name")), "name"));
        workflow.setDescription(asString(request.get("description")));
        workflow.setStatus(defaultString(asString(request.get("status")), "DRAFT"));
        workflow.setCreatedBy(defaultString(TenantContext.getUserId(), "system"));
        workflow.setActiveDefinitionVersion(1);
        return workflowRepository.save(workflow);
    }

    @Transactional
    public Workflow updateWorkflow(String workflowId, Map<String, Object> request) {
        Workflow workflow = findWorkflow(workflowId);
        String name = asString(request.get("name"));
        if (name != null) {
            workflow.setName(name);
        }
        if (request.containsKey("description")) {
            workflow.setDescription(asString(request.get("description")));
        }
        if (request.containsKey("teamId")) {
            workflow.setTeamId(asString(request.get("teamId")));
        }
        if (request.containsKey("ownershipScope")) {
            workflow.setOwnershipScope(defaultString(asString(request.get("ownershipScope")), "WORKSPACE"));
        }
        return workflowRepository.save(workflow);
    }

    @Transactional
    public WorkflowDefinition saveDefinition(String workflowId, Integer version, WorkflowGraphDto graph, boolean publishDefinition) {
        Workflow workflow = findWorkflow(workflowId);
        WorkflowGraphDto normalized = workflowGraphValidator.validateAndNormalize(graph);

        Integer targetVersion = version;
        if (targetVersion == null || targetVersion < 1) {
            targetVersion = workflowDefinitionRepository
                    .findTopByTenantIdAndWorkspaceIdAndWorkflowIdOrderByVersionDesc(requireTenant(), requireWorkspace(), workflowId)
                    .map(def -> def.getVersion() + 1)
                    .orElse(1);
        }

        WorkflowDefinition definition = workflowDefinitionRepository
                .findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId(workflowId, targetVersion, requireTenant(), requireWorkspace())
                .orElseGet(WorkflowDefinition::new);

        definition.setWorkflowId(workflowId);
        definition.setVersion(targetVersion);
        definition.setTenantId(requireTenant());
        definition.setWorkspaceId(requireWorkspace());
        definition.setGraphVersion(normalized.getGraphVersion());
        definition.setPublished(publishDefinition);
        try {
            definition.setDefinition(objectMapper.writeValueAsString(normalized));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize workflow graph", e);
        }

        WorkflowDefinition saved = workflowDefinitionRepository.save(definition);
        if (publishDefinition) {
            workflow.setActiveDefinitionVersion(saved.getVersion());
            if (!"ACTIVE".equals(workflow.getStatus())) {
                enforceTransition(workflow, "ACTIVE");
            }
            workflowRepository.save(workflow);
        }
        return saved;
    }

    public WorkflowDefinition getLatestDefinition(String workflowId) {
        return workflowDefinitionRepository
                .findTopByTenantIdAndWorkspaceIdAndWorkflowIdOrderByVersionDesc(requireTenant(), requireWorkspace(), workflowId)
                .orElseThrow(() -> new EntityNotFoundException("No definition found"));
    }

    public WorkflowDefinition getDefinitionVersion(String workflowId, Integer version) {
        return workflowDefinitionRepository
                .findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId(workflowId, version, requireTenant(), requireWorkspace())
                .orElseThrow(() -> new EntityNotFoundException("Definition not found"));
    }

    public List<WorkflowDefinition> listVersions(String workflowId) {
        return workflowDefinitionRepository
                .findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByVersionDesc(requireTenant(), requireWorkspace(), workflowId);
    }

    @Transactional
    public Workflow publish(String workflowId, Integer version) {
        Workflow workflow = findWorkflow(workflowId);
        WorkflowDefinition definition;
        if (version == null) {
            definition = getLatestDefinition(workflowId);
        } else {
            definition = getDefinitionVersion(workflowId, version);
        }
        definition.setPublished(true);
        workflowDefinitionRepository.save(definition);
        workflow.setActiveDefinitionVersion(definition.getVersion());
        enforceTransition(workflow, "ACTIVE");
        return workflowRepository.save(workflow);
    }

    @Transactional
    public Workflow pause(String workflowId) {
        Workflow workflow = findWorkflow(workflowId);
        enforceTransition(workflow, "PAUSED");
        return workflowRepository.save(workflow);
    }

    @Transactional
    public Workflow resume(String workflowId) {
        Workflow workflow = findWorkflow(workflowId);
        enforceTransition(workflow, "ACTIVE");
        return workflowRepository.save(workflow);
    }

    @Transactional
    public Workflow stop(String workflowId) {
        Workflow workflow = findWorkflow(workflowId);
        enforceTransition(workflow, "STOPPED");
        return workflowRepository.save(workflow);
    }

    @Transactional
    public Workflow archive(String workflowId) {
        Workflow workflow = findWorkflow(workflowId);
        enforceTransition(workflow, "ARCHIVED");
        workflow.setArchivedAt(Instant.now());
        return workflowRepository.save(workflow);
    }

    @Transactional
    public Workflow rollback(String workflowId, Integer version) {
        if (version == null) {
            throw new IllegalArgumentException("version is required for rollback");
        }
        Workflow workflow = findWorkflow(workflowId);
        WorkflowDefinition definition = getDefinitionVersion(workflowId, version);
        workflow.setActiveDefinitionVersion(definition.getVersion());
        workflow.setStatus("ROLLED_BACK");
        return workflowRepository.save(workflow);
    }

    @Transactional
    public Workflow cloneWorkflow(String workflowId) {
        Workflow source = findWorkflow(workflowId);
        Workflow clone = new Workflow();
        clone.setTenantId(source.getTenantId());
        clone.setWorkspaceId(source.getWorkspaceId());
        clone.setTeamId(source.getTeamId());
        clone.setOwnershipScope(source.getOwnershipScope());
        clone.setName(source.getName() + " (Copy)");
        clone.setDescription(source.getDescription());
        clone.setStatus("DRAFT");
        clone.setCreatedBy(defaultString(TenantContext.getUserId(), source.getCreatedBy()));
        clone.setActiveDefinitionVersion(1);
        Workflow savedClone = workflowRepository.save(clone);

        Optional<WorkflowDefinition> latestDefinition = workflowDefinitionRepository
                .findTopByTenantIdAndWorkspaceIdAndWorkflowIdOrderByVersionDesc(requireTenant(), requireWorkspace(), source.getId());
        if (latestDefinition.isPresent()) {
            WorkflowDefinition copied = new WorkflowDefinition();
            copied.setWorkflowId(savedClone.getId());
            copied.setVersion(1);
            copied.setTenantId(savedClone.getTenantId());
            copied.setWorkspaceId(savedClone.getWorkspaceId());
            copied.setGraphVersion(latestDefinition.get().getGraphVersion());
            copied.setDefinition(latestDefinition.get().getDefinition());
            copied.setPublished(false);
            workflowDefinitionRepository.save(copied);
        }
        return savedClone;
    }

    public Map<String, Object> validateGraph(WorkflowGraphDto graph) {
        WorkflowGraphDto normalized = workflowGraphValidator.validateAndNormalize(graph);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("graphVersion", normalized.getGraphVersion());
        response.put("nodeCount", normalized.getNodes().size());
        response.put("initialNodeId", normalized.getInitialNodeId());
        response.put("capabilities", graphCapabilities(normalized));
        return response;
    }

    public Map<String, Object> journeyCapabilities(String workflowId) {
        WorkflowGraphDto graph = parseDefinition(getLatestDefinition(workflowId));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("workflowId", workflowId);
        response.put("activeDefinitionVersion", findWorkflow(workflowId).getActiveDefinitionVersion());
        response.put("capabilities", graphCapabilities(graph));
        response.put("graphVersion", graph.getGraphVersion());
        return response;
    }

    public Map<String, Object> journeyAnalytics(String workflowId) {
        findWorkflow(workflowId);
        List<WorkflowInstance> runs = listRuns(workflowId);
        Map<String, Long> runStatusCounts = new LinkedHashMap<>();
        for (WorkflowInstance run : runs) {
            String status = run.getStatus() == null ? "UNKNOWN" : run.getStatus();
            runStatusCounts.put(status, runStatusCounts.getOrDefault(status, 0L) + 1);
        }
        Map<String, Long> nodeExecutions = new LinkedHashMap<>();
        for (WorkflowInstance run : runs.stream().limit(100).toList()) {
            for (InstanceHistory history : instanceHistoryRepository
                    .findByTenantIdAndWorkspaceIdAndInstanceIdOrderByExecutedAtDesc(requireTenant(), requireWorkspace(), run.getId())) {
                String key = history.getNodeId() == null ? "UNKNOWN" : history.getNodeId();
                nodeExecutions.put(key, nodeExecutions.getOrDefault(key, 0L) + 1);
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("workflowId", workflowId);
        response.put("runCount", runs.size());
        response.put("runStatusCounts", runStatusCounts);
        response.put("nodeExecutions", nodeExecutions);
        response.put("capabilities", journeyCapabilities(workflowId).get("capabilities"));
        return response;
    }

    public Map<String, Object> simulate(String workflowId, WorkflowGraphDto graph, Map<String, Object> context, boolean dryRun) {
        WorkflowGraphDto targetGraph = graph;
        if (targetGraph == null) {
            WorkflowDefinition definition = getLatestDefinition(workflowId);
            try {
                targetGraph = objectMapper.readValue(definition.getDefinition(), WorkflowGraphDto.class);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Workflow definition cannot be parsed", e);
            }
        }

        WorkflowGraphDto normalized = workflowGraphValidator.validateAndNormalize(targetGraph);
        List<String> visited = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String cursor = normalized.getInitialNodeId();
        int guard = 0;
        while (cursor != null && guard++ < 250) {
            if (!seen.add(cursor)) {
                visited.add(cursor + " (cycle)");
                break;
            }
            visited.add(cursor);
            WorkflowGraphDto.WorkflowNode node = normalized.getNodes().get(cursor);
            if (node == null || "END".equals(node.getType())) {
                break;
            }
            String next = node.getNextNodeId();
            if ((next == null || next.isBlank()) && node.getBranches() != null && !node.getBranches().isEmpty()) {
                String branchTarget = resolveBranch(node.getBranches(), context);
                next = branchTarget;
            }
            cursor = next;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("workflowId", workflowId);
        response.put("dryRun", dryRun);
        response.put("visitedNodes", visited);
        response.put("steps", visited.size());
        response.put("truncated", guard >= 250);
        response.put("capabilities", graphCapabilities(normalized));
        response.put("entryAccepted", normalized.getEntryPolicy() == null || normalized.getEntryPolicy().isCheckSuppression());
        response.put("goalsReached", visited.stream()
                .filter(id -> !id.endsWith(" (cycle)"))
                .filter(id -> {
                    WorkflowGraphDto.WorkflowNode node = normalized.getNodes().get(id);
                    return node != null && "EXIT_GOAL".equals(node.getType());
                })
                .toList());
        response.put("exitReason", visited.stream().anyMatch(id -> id.endsWith(" (cycle)")) ? "CYCLE_DETECTED" : "NORMAL");
        return response;
    }

    public Map<String, Object> compareVersions(String workflowId, Integer leftVersion, Integer rightVersion) {
        if (leftVersion == null || rightVersion == null) {
            throw new IllegalArgumentException("leftVersion and rightVersion are required");
        }
        WorkflowDefinition leftDefinition = getDefinitionVersion(workflowId, leftVersion);
        WorkflowDefinition rightDefinition = getDefinitionVersion(workflowId, rightVersion);
        try {
            WorkflowGraphDto leftGraph = objectMapper.readValue(leftDefinition.getDefinition(), WorkflowGraphDto.class);
            WorkflowGraphDto rightGraph = objectMapper.readValue(rightDefinition.getDefinition(), WorkflowGraphDto.class);
            WorkflowGraphDto normalizedLeft = workflowGraphValidator.validateAndNormalize(leftGraph);
            WorkflowGraphDto normalizedRight = workflowGraphValidator.validateAndNormalize(rightGraph);

            Set<String> leftNodes = normalizedLeft.getNodes().keySet();
            Set<String> rightNodes = normalizedRight.getNodes().keySet();
            Set<String> added = new LinkedHashSet<>(rightNodes);
            added.removeAll(leftNodes);
            Set<String> removed = new LinkedHashSet<>(leftNodes);
            removed.removeAll(rightNodes);
            Set<String> common = new LinkedHashSet<>(leftNodes);
            common.retainAll(rightNodes);
            int changed = 0;
            for (String nodeId : common) {
                WorkflowGraphDto.WorkflowNode leftNode = normalizedLeft.getNodes().get(nodeId);
                WorkflowGraphDto.WorkflowNode rightNode = normalizedRight.getNodes().get(nodeId);
                if (!objectMapper.writeValueAsString(leftNode).equals(objectMapper.writeValueAsString(rightNode))) {
                    changed += 1;
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("workflowId", workflowId);
            response.put("leftVersion", leftVersion);
            response.put("rightVersion", rightVersion);
            response.put("addedNodes", added);
            response.put("removedNodes", removed);
            response.put("changedNodeCount", changed);
            return response;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to compare workflow versions", e);
        }
    }

    public Map<String, Object> triggerWorkflow(String workflowId, Map<String, Object> request) {
        Workflow workflow = findWorkflow(workflowId);
        String subscriberId = requiredValue(asString(request.get("subscriberId")), "subscriberId");
        Integer version = asInteger(request.get("version"));
        String idempotencyKey = defaultString(asString(request.get("idempotencyKey")), IdGenerator.newIdempotencyKey());
        @SuppressWarnings("unchecked")
        Map<String, Object> context = request.get("context") instanceof Map<?, ?> contextMap
                ? objectMapper.convertValue(contextMap, new TypeReference<>() {})
                : Map.of();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", workflowId);
        payload.put("version", version != null ? version : workflow.getActiveDefinitionVersion());
        payload.put("subscriberId", subscriberId);
        payload.put("context", context);
        payload.put("workspaceId", workflow.getWorkspaceId());
        payload.put("idempotencyKey", idempotencyKey);

        String previousRequestId = TenantContext.getRequestId();
        TenantContext.setRequestId(idempotencyKey);
        try {
            workflowEventPublisher.publishAction(AppConstants.TOPIC_WORKFLOW_TRIGGER, requireTenant(), workflowId + ":" + subscriberId, payload);
        } finally {
            TenantContext.setRequestId(previousRequestId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("workflowId", workflowId);
        response.put("subscriberId", subscriberId);
        response.put("version", payload.get("version"));
        response.put("idempotencyKey", idempotencyKey);
        response.put("accepted", true);
        return response;
    }

    public List<WorkflowInstance> listRuns(String workflowId) {
        return workflowInstanceRepository.findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByCreatedAtDesc(requireTenant(), requireWorkspace(), workflowId);
    }

    public WorkflowInstance getRun(String runId) {
        return workflowInstanceRepository.findByIdAndTenantIdAndWorkspaceId(runId, requireTenant(), requireWorkspace())
                .orElseThrow(() -> new EntityNotFoundException("Workflow run not found"));
    }

    public List<InstanceHistory> getRunSteps(String runId) {
        getRun(runId);
        return instanceHistoryRepository.findByTenantIdAndWorkspaceIdAndInstanceIdOrderByExecutedAtDesc(requireTenant(), requireWorkspace(), runId);
    }

    public Map<String, Object> getRunTrace(String runId) {
        WorkflowInstance run = getRun(runId);
        List<InstanceHistory> steps = getRunSteps(runId);
        return Map.of(
                "run", run,
                "steps", steps,
                "stepCount", steps.size()
        );
    }

    private Workflow findWorkflow(String workflowId) {
        return workflowRepository
                .findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(workflowId, requireTenant(), requireWorkspace())
                .orElseThrow(() -> new EntityNotFoundException("Workflow not found"));
    }

    private void enforceTransition(Workflow workflow, String targetStatus) {
        String current = workflow.getStatus() == null ? "DRAFT" : workflow.getStatus();
        if (current.equals(targetStatus)) {
            return;
        }
        Set<String> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowedTargets.contains(targetStatus)) {
            throw new IllegalStateException("Invalid workflow transition: " + current + " -> " + targetStatus);
        }
        workflow.setStatus(targetStatus);
    }

    private String resolveBranch(List<WorkflowGraphDto.ConditionEdge> branches, Map<String, Object> context) {
        if (branches == null || branches.isEmpty()) {
            return null;
        }
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        for (WorkflowGraphDto.ConditionEdge branch : branches) {
            String condition = branch.getCondition();
            if (condition == null || condition.isBlank()) {
                return branch.getTargetNodeId();
            }
            if ("true".equalsIgnoreCase(condition) || "true_path".equalsIgnoreCase(condition)) {
                return branch.getTargetNodeId();
            }
            Object value = safeContext.get(condition);
            if (Boolean.TRUE.equals(value)) {
                return branch.getTargetNodeId();
            }
        }
        return branches.getFirst().getTargetNodeId();
    }

    private WorkflowGraphDto parseDefinition(WorkflowDefinition definition) {
        try {
            return workflowGraphValidator.validateAndNormalize(objectMapper.readValue(definition.getDefinition(), WorkflowGraphDto.class));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Workflow definition cannot be parsed", e);
        }
    }

    private Map<String, Object> graphCapabilities(WorkflowGraphDto graph) {
        Map<String, Long> nodeTypeCounts = new LinkedHashMap<>();
        for (WorkflowGraphDto.WorkflowNode node : graph.getNodes().values()) {
            String type = node.getType() == null ? "UNKNOWN" : node.getType();
            nodeTypeCounts.put(type, nodeTypeCounts.getOrDefault(type, 0L) + 1);
        }
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("entrySources", nodeTypeCounts.getOrDefault("ENTRY_TRIGGER", 0L) + nodeTypeCounts.getOrDefault("EVENT_LISTENER", 0L));
        capabilities.put("waits", nodeTypeCounts.getOrDefault("DELAY", 0L) + nodeTypeCounts.getOrDefault("WAIT_UNTIL", 0L) + nodeTypeCounts.getOrDefault("PAUSE", 0L));
        capabilities.put("decisions", nodeTypeCounts.getOrDefault("CONDITION", 0L) + nodeTypeCounts.getOrDefault("BRANCH", 0L) + nodeTypeCounts.getOrDefault("SPLIT", 0L));
        capabilities.put("goals", nodeTypeCounts.getOrDefault("EXIT_GOAL", 0L));
        capabilities.put("exits", nodeTypeCounts.getOrDefault("END", 0L));
        capabilities.put("reentryGates", nodeTypeCounts.getOrDefault("REENTRY_GATE", 0L));
        capabilities.put("nodeTypeCounts", nodeTypeCounts);
        capabilities.put("reentryPolicy", graph.getReentryPolicy());
        capabilities.put("entryPolicy", graph.getEntryPolicy());
        return capabilities;
    }

    private String requireTenant() {
        return TenantContext.requireTenantId();
    }

    private String requireWorkspace() {
        return TenantContext.requireWorkspaceId();
    }

    private String requiredValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String defaultString(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
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
