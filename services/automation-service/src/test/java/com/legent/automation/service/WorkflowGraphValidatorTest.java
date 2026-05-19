package com.legent.automation.service;

import com.legent.automation.dto.WorkflowGraphDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowGraphValidatorTest {

    private final WorkflowGraphValidator validator = new WorkflowGraphValidator();

    @Test
    void validateRuntimeSupportedRejectsAdvancedNodesWithoutRuntimeHandlers() {
        WorkflowGraphDto graph = graph(
                "entry",
                Map.of(
                        "entry", node("entry", "ENTRY_TRIGGER", Map.of(), "wait", List.of()),
                        "wait", node("wait", "WAIT_UNTIL", Map.of("at", "2026-05-20T12:00:00Z"), "end", List.of()),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportErrors(graph))
                .containsExactly("node wait type WAIT_UNTIL is not supported by the live workflow runtime");
        assertThatThrownBy(() -> validator.validateRuntimeSupported(graph))
                .hasMessageContaining("unsupported runtime semantics")
                .hasMessageContaining("WAIT_UNTIL");
    }

    @Test
    void validateRuntimeSupportedRejectsSendEmailWithoutCampaignId() {
        WorkflowGraphDto graph = graph(
                "send",
                Map.of(
                        "send", node("send", "SEND_EMAIL", Map.of(), "end", List.of()),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportErrors(graph))
                .containsExactly("node send type SEND_EMAIL requires configuration.campaignId");
    }

    @Test
    void validateRuntimeSupportedRejectsInvalidDelayMinutes() {
        WorkflowGraphDto graph = graph(
                "delay",
                Map.of(
                        "delay", node("delay", "DELAY", Map.of("minutes", "never"), "end", List.of()),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportErrors(graph))
                .containsExactly("node delay type DELAY requires configuration.minutes between 1 and 10080");
    }

    @Test
    void validateRuntimeSupportedAcceptsImplementedRuntimeSubset() {
        WorkflowGraphDto graph = graph(
                "entry",
                Map.of(
                        "entry", node("entry", "ENTRY_TRIGGER", Map.of(), "send", List.of()),
                        "send", node("send", "SEND_EMAIL", Map.of("campaignId", "campaign-1"), "delay", List.of()),
                        "delay", node("delay", "DELAY", Map.of("minutes", 5), "condition", List.of()),
                        "condition", node("condition", "CONDITION", Map.of(), null,
                                List.of(new WorkflowGraphDto.ConditionEdge("hasOpened == true", "end"))),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportErrors(graph)).isEmpty();
    }

    private WorkflowGraphDto graph(String initialNodeId, Map<String, WorkflowGraphDto.WorkflowNode> nodes) {
        WorkflowGraphDto graph = new WorkflowGraphDto();
        graph.setInitialNodeId(initialNodeId);
        graph.setNodes(nodes);
        return graph;
    }

    private WorkflowGraphDto.WorkflowNode node(
            String id,
            String type,
            Map<String, Object> configuration,
            String nextNodeId,
            List<WorkflowGraphDto.ConditionEdge> branches) {
        WorkflowGraphDto.WorkflowNode node = new WorkflowGraphDto.WorkflowNode();
        node.setId(id);
        node.setType(type);
        node.setConfiguration(configuration);
        node.setNextNodeId(nextNodeId);
        node.setBranches(branches);
        return node;
    }
}
