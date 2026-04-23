package com.legent.automation.service;

import com.legent.cache.service.CacheService;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.legent.automation.service.node.NodeHandler;
import com.legent.automation.event.WorkflowEventPublisher;
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
    private final InstanceHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, NodeHandler> nodeHandlers;
    private final CacheService cacheService;

    private final WorkflowEventPublisher eventPublisher;

        public WorkflowEngine(WorkflowInstanceRepository instanceRepository,
                  WorkflowDefinitionRepository definitionRepository,
                  InstanceHistoryRepository historyRepository,
                  ObjectMapper objectMapper,
                  List<NodeHandler> handlerList,
                  CacheService cacheService,
                  WorkflowEventPublisher eventPublisher) {
        this.instanceRepository = instanceRepository;
        this.definitionRepository = definitionRepository;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
        this.nodeHandlers = handlerList.stream()
            .collect(Collectors.toMap(NodeHandler::getType, Function.identity()));
        }

    @Async("workflowExecutor")
    @Transactional
    public void startWorkflow(String tenantId, String workflowId, Integer version, String subscriberId, Map<String, Object> initialContext) {
        
        WorkflowDefinition def = definitionRepository.findByWorkflowIdAndVersionAndTenantId(workflowId, version, tenantId)
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
        instance.setWorkflowId(workflowId);
        instance.setVersion(version);
        instance.setSubscriberId(subscriberId);
        
        try {
            instance.setContext(objectMapper.writeValueAsString(initialContext));
        } catch (JsonProcessingException e) {
            instance.setContext("{}");
        }

        instance.setCurrentNodeId(graph.getInitialNodeId());
        instance = instanceRepository.save(instance);

        executeFlowLoop(instance, graph);
    }

    @Async("workflowExecutor")
    @Transactional
    public void resumeInstance(String instanceId, String nextNodeId) {
        String lockKey = "wf:lock:" + instanceId;
        if (cacheService.get(lockKey, String.class).isPresent()) {
            log.warn("Instance {} is currently locked/processing. Dropping concurrent resume call.", instanceId);
            return;
        }

        try {
            cacheService.set(lockKey, "1", java.time.Duration.ofMinutes(5));

            WorkflowInstance instance = instanceRepository.findById(instanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Instance missing"));

            WorkflowDefinition def = definitionRepository.findByWorkflowIdAndVersionAndTenantId(
                instance.getWorkflowId(), instance.getVersion(), instance.getTenantId()
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
                break;
            }

            try {
                // Execute Step
                NodeHandler handler = nodeHandlers.get(node.getType());
                if (handler == null) {
                    throw new IllegalStateException("Unknown node type: " + node.getType());
                }

                String nextId = handler.execute(instance, node);
                
                logHistory(instance, currId, "SUCCESS", null);
                // Emit workflow.step.completed event
                eventPublisher.publishAction(
                    "workflow.step.completed",
                    instance.getTenantId(),
                    instance.getId(),
                    Map.of(
                        "instanceId", instance.getId(),
                        "workflowId", instance.getWorkflowId(),
                        "nodeId", currId,
                        "nodeType", node.getType(),
                        "subscriberId", instance.getSubscriberId(),
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
                // Save progress periodically if linear execution is long, else save at end/suspend is enough
            } catch (Exception e) {
                log.error("Error executing node {} in instance {}", currId, instance.getId(), e);
                logHistory(instance, currId, "ERROR", e.getMessage());
                instance.setStatus("FAILED");
                instanceRepository.save(instance);
                break;
            }
        }
    }

    private void logHistory(WorkflowInstance instance, String nodeId, String status, String errorMsg) {
        InstanceHistory hist = new InstanceHistory();
        hist.setTenantId(instance.getTenantId());
        hist.setInstanceId(instance.getId());
        hist.setNodeId(nodeId);
        hist.setStatus(status);
        hist.setErrorMessage(errorMsg);
        historyRepository.save(hist);
    }
}
