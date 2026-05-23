package com.legent.automation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.domain.InstanceHistory;
import com.legent.automation.domain.Workflow;
import com.legent.automation.domain.WorkflowDefinition;
import com.legent.automation.domain.WorkflowInstance;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
    void listWorkflowsUsesDefaultFirstPage() {
        when(workflowRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                any(Pageable.class)))
                .thenReturn(List.of(workflow("DRAFT", 1)));

        List<Workflow> workflows = service.listWorkflows();

        assertThat(workflows).hasSize(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(workflowRepository).findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
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

    @Test
    @SuppressWarnings("unchecked")
    void journeyAnalyticsReturnsBoundedStepPathGoalAndDiagnostics() {
        Workflow workflow = workflow("ACTIVE", 1);
        WorkflowDefinition definition = definition(1, analyticsGraph(), true);
        List<WorkflowInstance> runs = List.of(
                run("run-1", "COMPLETED", null),
                run("run-2", "FAILED", "send")
        );
        List<InstanceHistory> history = List.of(
                history("run-1", "entry", "SUCCESS", "2026-05-20T10:00:00Z"),
                history("run-1", "split", "SUCCESS", "2026-05-20T10:01:00Z"),
                history("run-1", "goal", "SUCCESS", "2026-05-20T10:02:00Z"),
                history("run-2", "entry", "SUCCESS", "2026-05-20T11:00:00Z"),
                history("run-2", "split", "SUCCESS", "2026-05-20T11:01:00Z"),
                history("run-2", "send", "ERROR", "2026-05-20T11:02:00Z")
        );

        when(workflowRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(WORKFLOW_ID, TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findTopByTenantIdAndWorkspaceIdAndWorkflowIdOrderByVersionDesc(TENANT_ID, WORKSPACE_ID, WORKFLOW_ID))
                .thenReturn(Optional.of(definition));
        when(workflowInstanceRepository.findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(WORKFLOW_ID),
                any(Pageable.class)))
                .thenReturn(runs);
        when(instanceHistoryRepository.findScopedHistoryForInstances(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                anyCollection()))
                .thenReturn(history);

        Map<String, Object> result = service.journeyAnalytics(WORKFLOW_ID);

        assertThat(result)
                .containsEntry("workflowId", WORKFLOW_ID)
                .containsEntry("runCount", 2);
        assertThat((Map<String, Long>) result.get("runStatusCounts"))
                .containsEntry("COMPLETED", 1L)
                .containsEntry("FAILED", 1L);
        assertThat((List<Map<String, Object>>) result.get("stepMetrics"))
                .anySatisfy(step -> assertThat(step)
                        .containsEntry("nodeId", "goal")
                        .containsEntry("completed", 1L));
        assertThat((List<Map<String, Object>>) result.get("topPaths"))
                .anySatisfy(path -> assertThat((String) path.get("signature")).contains("entry -> split -> goal"));
        assertThat((List<Map<String, Object>>) result.get("pathTests"))
                .anySatisfy(test -> assertThat(test)
                        .containsEntry("nodeId", "split")
                        .containsKey("observedTargets"));
        assertThat((List<Map<String, Object>>) result.get("conversionGoals"))
                .anySatisfy(goal -> assertThat(goal)
                        .containsEntry("goalId", "goal")
                        .containsEntry("hits", 1L));
        assertThat((Map<String, Object>) result.get("experimentScopes"))
                .containsEntry("separated", true);
        assertThat((List<String>) result.get("evidenceNotes"))
                .anySatisfy(note -> assertThat(note).contains("not causal attribution"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(workflowInstanceRepository).findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(WORKFLOW_ID),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void listRunsUsesDefaultPageRequest() {
        when(workflowInstanceRepository.findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(WORKFLOW_ID),
                any(Pageable.class)))
                .thenReturn(List.of(run("run-1", "COMPLETED", null)));

        List<WorkflowInstance> runs = service.listRuns(WORKFLOW_ID);

        assertThat(runs).hasSize(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(workflowInstanceRepository).findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(WORKFLOW_ID),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void listRunsClampsNegativePageAndOversizedLimit() {
        when(workflowInstanceRepository.findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(WORKFLOW_ID),
                any(Pageable.class)))
                .thenReturn(List.of());

        service.listRuns(WORKFLOW_ID, -4, 10_000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(workflowInstanceRepository).findByTenantIdAndWorkspaceIdAndWorkflowIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(WORKFLOW_ID),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void getRunStepsClampsHistoryLimitAndKeepsRunScoped() {
        WorkflowInstance run = run("run-1", "COMPLETED", null);
        when(workflowInstanceRepository.findByIdAndTenantIdAndWorkspaceId("run-1", TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(run));
        when(instanceHistoryRepository.findByTenantIdAndWorkspaceIdAndInstanceIdOrderByExecutedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq("run-1"),
                any(Pageable.class)))
                .thenReturn(List.of(history("run-1", "entry", "SUCCESS", "2026-05-20T10:00:00Z")));

        List<InstanceHistory> steps = service.getRunSteps("run-1", 5_000);

        assertThat(steps).hasSize(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(instanceHistoryRepository).findByTenantIdAndWorkspaceIdAndInstanceIdOrderByExecutedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq("run-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(500);
    }

    @Test
    void getRunTraceUsesDefaultBoundedHistoryLimit() {
        WorkflowInstance run = run("run-1", "COMPLETED", null);
        when(workflowInstanceRepository.findByIdAndTenantIdAndWorkspaceId("run-1", TENANT_ID, WORKSPACE_ID))
                .thenReturn(Optional.of(run));
        when(instanceHistoryRepository.findByTenantIdAndWorkspaceIdAndInstanceIdOrderByExecutedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq("run-1"),
                any(Pageable.class)))
                .thenReturn(List.of());

        Map<String, Object> trace = service.getRunTrace("run-1");

        assertThat(trace)
                .containsEntry("run", run)
                .containsEntry("stepCount", 0);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(instanceHistoryRepository).findByTenantIdAndWorkspaceIdAndInstanceIdOrderByExecutedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq("run-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
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

    private WorkflowGraphDto analyticsGraph() {
        WorkflowGraphDto.WorkflowNode entry = new WorkflowGraphDto.WorkflowNode(
                "entry",
                "ENTRY_TRIGGER",
                Map.of("label", "Entry"),
                "split",
                null
        );
        WorkflowGraphDto.WorkflowNode split = new WorkflowGraphDto.WorkflowNode(
                "split",
                "SPLIT",
                Map.of("label", "Path test"),
                null,
                List.of(
                        new WorkflowGraphDto.ConditionEdge("true", "goal"),
                        new WorkflowGraphDto.ConditionEdge("false", "send")
                )
        );
        WorkflowGraphDto.WorkflowNode goal = new WorkflowGraphDto.WorkflowNode(
                "goal",
                "EXIT_GOAL",
                Map.of("label", "Purchased"),
                null,
                null
        );
        WorkflowGraphDto.WorkflowNode send = new WorkflowGraphDto.WorkflowNode(
                "send",
                "SEND_EMAIL",
                Map.of("label", "Follow-up send"),
                null,
                null
        );
        return graph("entry", Map.of("entry", entry, "split", split, "goal", goal, "send", send));
    }

    private WorkflowInstance run(String id, String status, String currentNodeId) {
        WorkflowInstance run = new WorkflowInstance();
        run.setId(id);
        run.setTenantId(TENANT_ID);
        run.setWorkspaceId(WORKSPACE_ID);
        run.setWorkflowId(WORKFLOW_ID);
        run.setVersion(1);
        run.setSubscriberId("subscriber-" + id);
        run.setStatus(status);
        run.setCurrentNodeId(currentNodeId);
        return run;
    }

    private InstanceHistory history(String runId, String nodeId, String status, String executedAt) {
        InstanceHistory history = new InstanceHistory();
        history.setId("hist-" + runId + "-" + nodeId + "-" + status);
        history.setTenantId(TENANT_ID);
        history.setWorkspaceId(WORKSPACE_ID);
        history.setInstanceId(runId);
        history.setNodeId(nodeId);
        history.setStatus(status);
        history.setExecutedAt(Instant.parse(executedAt));
        return history;
    }

    private WorkflowGraphDto graph(String initialNodeId, Map<String, WorkflowGraphDto.WorkflowNode> nodes) {
        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setGraphVersion(2);
        graph.setInitialNodeId(initialNodeId);
        graph.setNodes(nodes);
        return graph;
    }
}
