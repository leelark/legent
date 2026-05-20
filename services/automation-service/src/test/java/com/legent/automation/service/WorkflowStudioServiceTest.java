package com.legent.automation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.domain.Workflow;
import com.legent.automation.domain.WorkflowDefinition;
import com.legent.automation.dto.WorkflowGraphDto;
import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.automation.repository.InstanceHistoryRepository;
import com.legent.automation.repository.WorkflowDefinitionRepository;
import com.legent.automation.repository.WorkflowInstanceRepository;
import com.legent.automation.repository.WorkflowRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowStudioServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String WORKFLOW_ID = "workflow-1";

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Mock
    private WorkflowInstanceRepository workflowInstanceRepository;
    @Mock
    private InstanceHistoryRepository instanceHistoryRepository;
    @Mock
    private WorkflowEventPublisher workflowEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowGraphValidator workflowGraphValidator = new WorkflowGraphValidator();
    private WorkflowStudioService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
        service = new WorkflowStudioService(
                workflowRepository,
                workflowDefinitionRepository,
                workflowInstanceRepository,
                instanceHistoryRepository,
                workflowGraphValidator,
                workflowEventPublisher,
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void saveDefinitionRejectsPublishedVersionOverwrite() {
        Workflow workflow = workflow("DRAFT", 1);
        WorkflowDefinition existing = definition(1, supportedGraph(), true);

        when(workflowRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(WORKFLOW_ID, TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId(WORKFLOW_ID, 1, TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.saveDefinition(WORKFLOW_ID, 1, supportedGraph(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable");

        verify(workflowDefinitionRepository, never()).save(any());
    }

    @Test
    void saveDefinitionValidatesActiveVersionEvenWhenSavingDraft() {
        Workflow workflow = workflow("ACTIVE", 1);

        when(workflowRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(WORKFLOW_ID, TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId(WORKFLOW_ID, 1, TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveDefinition(WORKFLOW_ID, 1, unsupportedGraph(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported runtime semantics");

        verify(workflowDefinitionRepository, never()).save(any());
    }

    @Test
    void rollbackRejectsUnsupportedRuntimeDefinition() {
        Workflow workflow = workflow("PAUSED", 1);
        WorkflowDefinition target = definition(2, unsupportedGraph(), false);

        when(workflowRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(WORKFLOW_ID, TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId(WORKFLOW_ID, 2, TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.rollback(WORKFLOW_ID, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported runtime semantics");

        verify(workflowRepository, never()).save(any());
    }

    @Test
    void resumeRejectsUnsupportedActiveDefinition() {
        Workflow workflow = workflow("PAUSED", 1);
        WorkflowDefinition active = definition(1, unsupportedGraph(), false);

        when(workflowRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(WORKFLOW_ID, TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByWorkflowIdAndVersionAndTenantIdAndWorkspaceId(WORKFLOW_ID, 1, TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.resume(WORKFLOW_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported runtime semantics");

        verify(workflowRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void simulateStopsAtUnsupportedRuntimeNode() {
        Map<String, Object> result = service.simulate(WORKFLOW_ID, unsupportedGraph(), Map.of(), true);

        assertThat(result)
                .containsEntry("runtimeSupported", false)
                .containsEntry("exitReason", "UNSUPPORTED_RUNTIME_NODE");
        assertThat((List<String>) result.get("visitedNodes")).containsExactly("webhook");
        assertThat((List<String>) result.get("runtimeErrors"))
                .anySatisfy(error -> assertThat(error).contains("WEBHOOK").contains("not supported"));
    }

    private Workflow workflow(String status, Integer activeVersion) {
        Workflow workflow = new Workflow();
        workflow.setId(WORKFLOW_ID);
        workflow.setTenantId(TENANT_ID);
        workflow.setWorkspaceId(WORKSPACE_ID);
        workflow.setName("Lifecycle nurture");
        workflow.setStatus(status);
        workflow.setActiveDefinitionVersion(activeVersion);
        return workflow;
    }

    private WorkflowDefinition definition(Integer version, WorkflowGraphDto graph, boolean published) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setWorkflowId(WORKFLOW_ID);
        definition.setVersion(version);
        definition.setTenantId(TENANT_ID);
        definition.setWorkspaceId(WORKSPACE_ID);
        definition.setGraphVersion(2);
        definition.setPublished(published);
        try {
            definition.setDefinition(objectMapper.writeValueAsString(graph));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return definition;
    }

    private WorkflowGraphDto supportedGraph() {
        WorkflowGraphDto.WorkflowNode entry = new WorkflowGraphDto.WorkflowNode(
                "entry",
                "ENTRY_TRIGGER",
                Map.of(),
                "end",
                null
        );
        WorkflowGraphDto.WorkflowNode end = new WorkflowGraphDto.WorkflowNode(
                "end",
                "END",
                Map.of(),
                null,
                null
        );
        return graph("entry", Map.of("entry", entry, "end", end));
    }

    private WorkflowGraphDto unsupportedGraph() {
        WorkflowGraphDto.WorkflowNode webhook = new WorkflowGraphDto.WorkflowNode(
                "webhook",
                "WEBHOOK",
                Map.of("url", "https://example.invalid/hook"),
                "end",
                null
        );
        WorkflowGraphDto.WorkflowNode end = new WorkflowGraphDto.WorkflowNode(
                "end",
                "END",
                Map.of(),
                null,
                null
        );
        return graph("webhook", Map.of("webhook", webhook, "end", end));
    }

    private WorkflowGraphDto graph(String initialNodeId, Map<String, WorkflowGraphDto.WorkflowNode> nodes) {
        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setGraphVersion(2);
        graph.setInitialNodeId(initialNodeId);
        graph.setNodes(nodes);
        return graph;
    }
}
