package com.legent.automation.service;

import com.legent.cache.service.CacheService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import java.util.List;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.legent.automation.domain.InstanceHistory;
import com.legent.automation.domain.WorkflowDefinition;
import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.automation.repository.InstanceHistoryRepository;
import com.legent.automation.repository.WorkflowDefinitionRepository;
import com.legent.automation.repository.WorkflowInstanceRepository;
import com.legent.automation.repository.WorkflowRepository;
import com.legent.automation.service.node.NodeHandler;
import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service

public class WorkflowEngine {

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowRepository workflowRepository;
    private final InstanceHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, NodeHandler> nodeHandlers;
    private final CacheService cacheService;

    private final WorkflowEventPublisher eventPublisher;
    private final AutomationEventIdempotencyService idempotencyService;

        public WorkflowEngine(WorkflowInstanceRepository instanceRepository,
                  WorkflowDefinitionRepository definitionRepository,
                  WorkflowRepository workflowRepository,
                  InstanceHistoryRepository historyRepository,
                  ObjectMapper objectMapper,
                  List<NodeHandler> handlerList,
                  CacheService cacheService,
                  WorkflowEventPublisher eventPublisher,
                  AutomationEventIdempotencyService idempotencyService) {
        this.instanceRepository = instanceRepository;
        this.definitionRepository = definitionRepository;
        this.workflowRepository = workflowRepository;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
        this.idempotencyService = idempotencyService;
        // LEGENT-HIGH-008: Use merge function to provide clear error message on duplicate handlers
        this.nodeHandlers = handlerList.stream()
            .collect(Collectors.toMap(
                NodeHandler::getType,
                Function.identity(),
                (existing, replacement) -> {
                    throw new IllegalStateException(
                        "Duplicate node handler for type: " + existing.getType() +
                        ". Classes: " + existing.getClass().getName() +
                        " vs " + replacement.getClass().getName());
                }
            ));
        }

    @Transactional
    public void startWorkflow(String tenantId,
                              String workspaceId,
                              String workflowId,
                              Integer version,
                              String subscriberId,
                              Map<String, Object> initialContext,
                              String environmentId,
                              String actorId,
                              String requestId,
                              String correlationId) {
        TenantContext.setTenantId(tenantId);
        TenantContext.setWorkspaceId(workspaceId);
        TenantContext.setEnvironmentId(environmentId);
        TenantContext.setUserId(actorId);
        TenantContext.setRequestId(requestId);
        TenantContext.setCorrelationId(correlationId);
        try {
            startWorkflowInternal(tenantId, workspaceId, workflowId, version, subscriberId, initialContext);
        } finally {
            TenantContext.clear();
        }
    }

    private void startWorkflowInternal(String tenantId,
                                       String workspaceId,
                                       String workflowId,
                                       Integer version,
                                       String subscriberId,
                                       Map<String, Object> initialContext) {
        com.legent.automation.domain.Workflow workflow = workflowRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(workflowId, tenantId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));

        if (!"ACTIVE".equals(workflow.getStatus()) && !"SCHEDULED".equals(workflow.getStatus())) {
            throw new IllegalStateException("Cannot start workflow. Current status: " + workflow.getStatus());
        }

        Integer effectiveVersion = version != null ? version : workflow.getActiveDefinitionVersion();
        if (effectiveVersion == null) {
            effectiveVersion = 1;
        }

        WorkflowDefinition def = definitionRepository.findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId(workflowId, effectiveVersion, tenantId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Definition not found"));

        WorkflowGraphDto graph;
        try {
            graph = objectMapper.readValue(def.getDefinition(), WorkflowGraphDto.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid JSON graph definition", e);
        }

        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(UUID.randomUUID().toString());
        instance.setTenantId(tenantId);
        instance.setWorkspaceId(workspaceId);
        instance.setTeamId(workflow.getTeamId());
        instance.setOwnershipScope(workflow.getOwnershipScope());
        instance.setEnvironmentId(TenantContext.getEnvironmentId());
        instance.setRequestId(TenantContext.getRequestId());
        instance.setCorrelationId(TenantContext.getCorrelationId());
        instance.setWorkflowId(workflowId);
        instance.setVersion(effectiveVersion);
        instance.setSubscriberId(subscriberId);
        instance.setStatus("RUNNING");
        
        try {
            instance.setContext(objectMapper.writeValueAsString(initialContext));
        } catch (JsonProcessingException e) {
            instance.setContext("{}");
        }

        instance.setCurrentNodeId(graph.getInitialNodeId());
        instance = instanceRepository.save(instance);

        eventPublisher.publishAction(
                AppConstants.TOPIC_WORKFLOW_STARTED,
                tenantId,
                instance.getId(),
                Map.of(
                        "instanceId", instance.getId(),
                        "workflowId", workflowId,
                        "workflowVersion", effectiveVersion,
                        "subscriberId", subscriberId,
                        "workspaceId", workspaceId
                )
        );

        executeFlowLoop(instance, graph);
    }

    @Async("workflowExecutor")
    @Transactional
    public void resumeInstance(String instanceId, String nextNodeId, String wakeId) {
        try {
            resumeInstanceInternal(instanceId, nextNodeId, wakeId);
        } finally {
            TenantContext.clear();
        }
    }

    private void resumeInstanceInternal(String instanceId, String nextNodeId, String wakeId) {
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Instance missing"));

        // Set context from the instance
        TenantContext.setTenantId(instance.getTenantId());
        TenantContext.setWorkspaceId(instance.getWorkspaceId());
        TenantContext.setEnvironmentId(instance.getEnvironmentId());
        TenantContext.setRequestId(instance.getRequestId());
        TenantContext.setCorrelationId(instance.getCorrelationId());

        if (wakeId != null && !wakeId.isBlank()
                && !idempotencyService.registerIfNew(
                instance.getTenantId(),
                instance.getWorkspaceId(),
                "workflow.delay.wake",
                wakeId,
                wakeId)) {
            log.info("Skipping duplicate workflow wake {}", wakeId);
            return;
        }

        String lockKey = "wf:lock:" + instanceId;
        if (cacheService.get(lockKey, String.class).isPresent()) {
            log.warn("Instance {} is currently locked/processing. Dropping concurrent resume call.", instanceId);
            return;
        }

        try {
            cacheService.set(lockKey, "1", java.time.Duration.ofMinutes(5));

            WorkflowDefinition def = definitionRepository.findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId(
                instance.getWorkflowId(), instance.getVersion(), instance.getTenantId(), instance.getWorkspaceId()
            ).orElseThrow();

            WorkflowGraphDto graph;
            try {
                graph = objectMapper.readValue(def.getDefinition(), WorkflowGraphDto.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Parse error", e);
            }

            instance.setCurrentNodeId(nextNodeId);
            instance.setStatus("RUNNING");
            instanceRepository.save(instance);

            executeFlowLoop(instance, graph);
        } finally {
            cacheService.delete(lockKey);
        }
    }

    private void executeFlowLoop(WorkflowInstance instance, WorkflowGraphDto graph) {
        String currId = instance.getCurrentNodeId();

        while (currId != null && "RUNNING".equals(instance.getStatus())) {
            WorkflowGraphDto.WorkflowNode node = graph.getNodes().get(currId);

            if (node == null || "END".equals(node.getType())) {
                instance.setStatus("COMPLETED");
                instance.setCurrentNodeId(null);
                instanceRepository.save(instance);
                log.info("Workflow instance {} COMPLETED", instance.getId());
                eventPublisher.publishAction(
                        AppConstants.TOPIC_WORKFLOW_COMPLETED,
                        instance.getTenantId(),
                        instance.getId(),
                        Map.of(
                                "instanceId", instance.getId(),
                                "workflowId", instance.getWorkflowId(),
                                "subscriberId", instance.getSubscriberId(),
                                "workspaceId", instance.getWorkspaceId(),
                                "status", "COMPLETED"
                        )
                );
                break;
            }

            try {
                // Execute Step
                NodeHandler handler = nodeHandlers.get(node.getType());
                if (handler == null) {
                    handler = nodeHandlers.get("*");
                }
                if (handler == null) {
                    throw new IllegalStateException("Unknown node type: " + node.getType());
                }

                eventPublisher.publishAction(
                        AppConstants.TOPIC_WORKFLOW_STEP_STARTED,
                        instance.getTenantId(),
                        instance.getId(),
                        Map.of(
                                "instanceId", instance.getId(),
                                "workflowId", instance.getWorkflowId(),
                                "nodeId", currId,
                                "nodeType", node.getType(),
                                "subscriberId", instance.getSubscriberId(),
                                "workspaceId", instance.getWorkspaceId()
                        )
                );

                String nextId = handler.execute(instance, node);

                logHistory(instance, currId, "SUCCESS", AppConstants.TOPIC_WORKFLOW_STEP_COMPLETED, null);
                // Emit workflow.step.completed event
                eventPublisher.publishAction(
                    AppConstants.TOPIC_WORKFLOW_STEP_COMPLETED,
                    instance.getTenantId(),
                    instance.getId(),
                    Map.of(
                        "instanceId", instance.getId(),
                        "workflowId", instance.getWorkflowId(),
                        "nodeId", currId,
                        "nodeType", node.getType(),
                        "subscriberId", instance.getSubscriberId(),
                        "workspaceId", instance.getWorkspaceId(),
                        "status", "SUCCESS"
                    )
                );

                // If handler returns null, it means the workflow suspended itself (e.g. DELAY node)
                if (nextId == null) {
                    instanceRepository.save(instance);
                    break;
                }

                currId = nextId;
                instance.setCurrentNodeId(currId);
                // Persist state between node executions to ensure recovery if service crashes
                instanceRepository.save(instance);
            } catch (Exception e) {
                log.error("Error executing node {} in instance {}", currId, instance.getId(), e);
                logHistory(instance, currId, "ERROR", AppConstants.TOPIC_WORKFLOW_STEP_FAILED, e.getMessage());
                instance.setStatus("FAILED");
                instanceRepository.save(instance);
                eventPublisher.publishAction(
                        AppConstants.TOPIC_WORKFLOW_STEP_FAILED,
                        instance.getTenantId(),
                        instance.getId(),
                        Map.of(
                                "instanceId", instance.getId(),
                                "workflowId", instance.getWorkflowId(),
                                "nodeId", currId,
                                "nodeType", node.getType(),
                                "subscriberId", instance.getSubscriberId(),
                                "workspaceId", instance.getWorkspaceId(),
                                "status", "FAILED",
                                "error", e.getMessage() == null ? "Unknown workflow step failure" : e.getMessage()
                        )
                );
                break;
            }
        }
    }

    private void logHistory(WorkflowInstance instance, String nodeId, String status, String eventType, String errorMsg) {
        InstanceHistory hist = new InstanceHistory();
        hist.setTenantId(instance.getTenantId());
        hist.setWorkspaceId(instance.getWorkspaceId());
        hist.setInstanceId(instance.getId());
        hist.setNodeId(nodeId);
        hist.setStatus(status);
        hist.setEventType(eventType);
        hist.setCorrelationId(TenantContext.getCorrelationId());
        hist.setDetails("{}");
        hist.setErrorMessage(errorMsg);
        historyRepository.save(hist);
    }
}
