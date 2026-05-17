package com.legent.automation.service;

import com.legent.cache.service.CacheService;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.legent.automation.domain.WorkflowDefinition;
import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.automation.repository.InstanceHistoryRepository;
import com.legent.automation.repository.WorkflowDefinitionRepository;
import com.legent.automation.repository.WorkflowInstanceRepository;
import com.legent.automation.repository.WorkflowRepository;
import com.legent.automation.service.node.DelayNodeHandler;
import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.automation.service.node.NodeHandler;
import com.legent.automation.service.node.SendEmailNodeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowEngine Unit Tests")

class WorkflowEngineTest {

    @Mock private WorkflowInstanceRepository instanceRepository;
    @Mock private WorkflowDefinitionRepository definitionRepository;
    @Mock private WorkflowRepository workflowRepository;
    @Mock private InstanceHistoryRepository historyRepository;
    @Mock private SendEmailNodeHandler sendEmailHandler;
    @Mock private DelayNodeHandler delayHandler;
    @Mock private CacheService cacheService;
    @Mock private WorkflowEventPublisher eventPublisher;
    @Mock private AutomationEventIdempotencyService idempotencyService;

    private WorkflowEngine workflowEngine;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(sendEmailHandler.getType()).thenReturn("SEND_EMAIL");
        when(delayHandler.getType()).thenReturn("DELAY");
        
        List<NodeHandler> handlers = List.of(sendEmailHandler, delayHandler);
        workflowEngine = new WorkflowEngine(instanceRepository, definitionRepository, workflowRepository, historyRepository, objectMapper, handlers, cacheService, eventPublisher, idempotencyService);
    }

    @Test
    void startWorkflow_executesAndCompletesLinearFlow() throws Exception {
        
        // Setup Definition
        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setInitialNodeId("node-1");
        
        WorkflowGraphDto.WorkflowNode node1 = new WorkflowGraphDto.WorkflowNode();
        node1.setId("node-1");
        node1.setType("SEND_EMAIL");
        node1.setNextNodeId("node-2");
        
        WorkflowGraphDto.WorkflowNode node2 = new WorkflowGraphDto.WorkflowNode();
        node2.setId("node-2");
        node2.setType("END");
        
        graph.setNodes(Map.of("node-1", node1, "node-2", node2));

        WorkflowDefinition def = new WorkflowDefinition();
        def.setDefinition(objectMapper.writeValueAsString(graph));

        com.legent.automation.domain.Workflow workflow = new com.legent.automation.domain.Workflow();
        workflow.setStatus("ACTIVE");
        workflow.setWorkspaceId("workspace-1");
        when(workflowRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("flow-1", "tenant-1", "workspace-1")).thenReturn(Optional.of(workflow));

        when(definitionRepository.findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId("flow-1", 1, "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(def));
                
        // Return same instance object on save to capture state changes
        when(instanceRepository.save(any(WorkflowInstance.class))).thenAnswer(i -> i.getArguments()[0]);
        
        when(sendEmailHandler.execute(any(), any())).thenReturn("node-2");

        workflowEngine.startWorkflow("tenant-1", "workspace-1", "flow-1", 1, "sub-1", Map.of(), "prod", "user-1", "req-1", "corr-1");

        verify(sendEmailHandler, times(1)).execute(any(), any());
        
        ArgumentCaptor<WorkflowInstance> captor = ArgumentCaptor.forClass(WorkflowInstance.class);
        verify(instanceRepository, atLeast(2)).save(captor.capture());
        
        WorkflowInstance lastSaved = captor.getValue();
        assertEquals("COMPLETED", lastSaved.getStatus());
        assertEquals(null, lastSaved.getCurrentNodeId());
    }

    @Test
    void startWorkflow_pausesOnDelayNode() throws Exception {
        
        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setInitialNodeId("node-1");
        
        WorkflowGraphDto.WorkflowNode node1 = new WorkflowGraphDto.WorkflowNode();
        node1.setId("node-1");
        node1.setType("DELAY");
        node1.setNextNodeId("node-2");
        
        graph.setNodes(Map.of("node-1", node1));

        WorkflowDefinition def = new WorkflowDefinition();
        def.setDefinition(objectMapper.writeValueAsString(graph));

        com.legent.automation.domain.Workflow workflow = new com.legent.automation.domain.Workflow();
        workflow.setStatus("ACTIVE");
        workflow.setWorkspaceId("workspace-1");
        when(workflowRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("flow-1", "tenant-1", "workspace-1")).thenReturn(Optional.of(workflow));

        when(definitionRepository.findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId("flow-1", 1, "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(def));
                
        when(instanceRepository.save(any(WorkflowInstance.class))).thenAnswer(i -> i.getArguments()[0]);
        
        // Delay handler returns null to halt flow
        when(delayHandler.execute(any(), any())).thenAnswer(invocation -> {
            WorkflowInstance inst = invocation.getArgument(0);
            inst.setStatus("WAITING");
            return null; 
        });

        workflowEngine.startWorkflow("tenant-1", "workspace-1", "flow-1", 1, "sub-1", Map.of(), "prod", "user-1", "req-1", "corr-1");

        ArgumentCaptor<WorkflowInstance> captor = ArgumentCaptor.forClass(WorkflowInstance.class);
        verify(instanceRepository, atLeast(2)).save(captor.capture());
        
        WorkflowInstance lastSaved = captor.getValue();
        assertEquals("WAITING", lastSaved.getStatus());
        assertEquals("node-1", lastSaved.getCurrentNodeId()); // Context stays parked at the delay node
    }

    @Test
    void resumeInstance_whenConcurrentResumeAttemptsOccur_onlyOneAcquiresLockAndExecutes() throws Exception {
        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setInitialNodeId("node-1");

        WorkflowGraphDto.WorkflowNode node1 = new WorkflowGraphDto.WorkflowNode();
        node1.setId("node-1");
        node1.setType("SEND_EMAIL");
        node1.setNextNodeId("end");

        WorkflowGraphDto.WorkflowNode end = new WorkflowGraphDto.WorkflowNode();
        end.setId("end");
        end.setType("END");

        graph.setNodes(Map.of("node-1", node1, "end", end));

        WorkflowDefinition def = new WorkflowDefinition();
        def.setDefinition(objectMapper.writeValueAsString(graph));

        when(instanceRepository.findById("instance-1")).thenAnswer(invocation -> Optional.of(waitingInstance()));
        when(cacheService.setIfAbsent(eq("wf:lock:instance-1"), eq("1"), eq(Duration.ofMinutes(5))))
                .thenAnswer(invocation -> lockHeld.compareAndSet(false, true));
        when(definitionRepository.findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId("flow-1", 1, "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(def));
        when(instanceRepository.save(any(WorkflowInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CountDownLatch firstExecutionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstExecution = new CountDownLatch(1);
        when(sendEmailHandler.execute(any(), any())).thenAnswer(invocation -> {
            firstExecutionStarted.countDown();
            assertTrue(releaseFirstExecution.await(5, TimeUnit.SECONDS));
            return "end";
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstResume = executor.submit(() -> workflowEngine.resumeInstance("instance-1", "node-1", null));
            assertTrue(firstExecutionStarted.await(5, TimeUnit.SECONDS));

            Future<?> concurrentResume = executor.submit(() -> workflowEngine.resumeInstance("instance-1", "node-1", null));
            concurrentResume.get(5, TimeUnit.SECONDS);

            releaseFirstExecution.countDown();
            firstResume.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(cacheService, times(2)).setIfAbsent("wf:lock:instance-1", "1", Duration.ofMinutes(5));
        verify(cacheService, times(1)).delete("wf:lock:instance-1");
        verify(cacheService, never()).get("wf:lock:instance-1", String.class);
        verify(sendEmailHandler, times(1)).execute(any(), any());
        verify(definitionRepository, times(1))
                .findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId("flow-1", 1, "tenant-1", "workspace-1");
    }

    private final AtomicBoolean lockHeld = new AtomicBoolean(false);

    private WorkflowInstance waitingInstance() {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId("instance-1");
        instance.setTenantId("tenant-1");
        instance.setWorkspaceId("workspace-1");
        instance.setEnvironmentId("prod");
        instance.setRequestId("req-1");
        instance.setCorrelationId("corr-1");
        instance.setWorkflowId("flow-1");
        instance.setVersion(1);
        instance.setSubscriberId("sub-1");
        instance.setStatus("WAITING");
        instance.setCurrentNodeId("node-1");
        instance.setContext("{}");
        return instance;
    }
}
