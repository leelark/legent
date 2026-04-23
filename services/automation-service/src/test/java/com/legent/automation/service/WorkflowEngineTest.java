package com.legent.automation.service;

import com.legent.cache.service.CacheService;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;

import java.util.List;

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
import static org.mockito.ArgumentMatchers.any;
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

    private WorkflowEngine workflowEngine;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(sendEmailHandler.getType()).thenReturn("SEND_EMAIL");
        when(delayHandler.getType()).thenReturn("DELAY");
        
        List<NodeHandler> handlers = List.of(sendEmailHandler, delayHandler);
        workflowEngine = new WorkflowEngine(instanceRepository, definitionRepository, workflowRepository, historyRepository, objectMapper, handlers, cacheService, eventPublisher);
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
        when(workflowRepository.findById("flow-1")).thenReturn(Optional.of(workflow));

        when(definitionRepository.findByWorkflowIdAndVersionAndTenantId("flow-1", 1, "tenant-1"))
                .thenReturn(Optional.of(def));
                
        // Return same instance object on save to capture state changes
        when(instanceRepository.save(any(WorkflowInstance.class))).thenAnswer(i -> i.getArguments()[0]);
        
        when(sendEmailHandler.execute(any(), any())).thenReturn("node-2");

        workflowEngine.startWorkflow("tenant-1", "flow-1", 1, "sub-1", Map.of());

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
        when(workflowRepository.findById("flow-1")).thenReturn(Optional.of(workflow));

        when(definitionRepository.findByWorkflowIdAndVersionAndTenantId("flow-1", 1, "tenant-1"))
                .thenReturn(Optional.of(def));
                
        when(instanceRepository.save(any(WorkflowInstance.class))).thenAnswer(i -> i.getArguments()[0]);
        
        // Delay handler returns null to halt flow
        when(delayHandler.execute(any(), any())).thenAnswer(invocation -> {
            WorkflowInstance inst = invocation.getArgument(0);
            inst.setStatus("WAITING");
            return null; 
        });

        workflowEngine.startWorkflow("tenant-1", "flow-1", 1, "sub-1", Map.of());

        ArgumentCaptor<WorkflowInstance> captor = ArgumentCaptor.forClass(WorkflowInstance.class);
        verify(instanceRepository, atLeast(2)).save(captor.capture());
        
        WorkflowInstance lastSaved = captor.getValue();
        assertEquals("WAITING", lastSaved.getStatus());
        assertEquals("node-1", lastSaved.getCurrentNodeId()); // Context stays parked at the delay node
    }
}
