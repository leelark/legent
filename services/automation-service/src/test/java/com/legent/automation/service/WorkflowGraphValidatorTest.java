package com.legent.automation.service;

import com.legent.automation.dto.WorkflowGraphDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowGraphValidatorTest {

    private final WorkflowGraphValidator validator = new WorkflowGraphValidator();

    @Test
    void validateRuntimeSupportedRejectsAdvancedNodesWithoutRuntimeHandlers() {
        WorkflowGraphDto graph = graph(
                "webhook",
                Map.of(
                        "webhook", node("webhook", "WEBHOOK", Map.of("url", "https://example.invalid/hook"), "end", List.of()),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportErrors(graph))
                .containsExactly("node webhook type WEBHOOK is not supported by the live workflow runtime");
        assertThatThrownBy(() -> validator.validateRuntimeSupported(graph))
                .hasMessageContaining("unsupported runtime semantics")
                .hasMessageContaining("WEBHOOK");
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
    void validateRuntimeSupportedRejectsSendEmailSafetyOverrides() {
        WorkflowGraphDto graph = graph(
                "send",
                Map.of(
                        "send", node("send", "SEND_EMAIL", Map.of(
                                "campaignId", "campaign-1",
                                "sender-email", "sender@example.com",
                                "skip_suppression", true), "end", List.of()),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportErrors(graph))
                .singleElement()
                .satisfies(error -> assertThat(error)
                        .contains("node send type SEND_EMAIL cannot override campaign recipients")
                        .contains("sender-email")
                        .contains("skip_suppression"));
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
    void validateRuntimeSupportedRejectsInvalidWaitUntilTimestamp() {
        WorkflowGraphDto graph = graph(
                "wait",
                Map.of(
                        "wait", node("wait", "WAIT_UNTIL", Map.of("at", "next quarter"), "end", List.of()),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportErrors(graph))
                .containsExactly("node wait type WAIT_UNTIL requires configuration.at or configuration.until as an ISO-8601 instant no more than 10080 minutes in the future");
    }

    @Test
    void validateRuntimeSupportedRejectsConditionAliasesWithoutBranches() {
        for (String type : List.of("BRANCH", "SPLIT")) {
            WorkflowGraphDto graph = graph(
                    "decision",
                    Map.of(
                            "decision", node("decision", type, Map.of(), "end", List.of()),
                            "end", node("end", "END", Map.of(), null, List.of())
                    ));

            assertThat(validator.runtimeSupportErrors(graph))
                    .containsExactly("node decision type " + type + " requires at least one branch");
        }
    }

    @Test
    void validateRuntimeSupportedRejectsFarFutureWaitUntilTimestamp() {
        WorkflowGraphDto graph = graph(
                "wait",
                Map.of(
                        "wait", node("wait", "WAIT_UNTIL", Map.of("at", Instant.now().plus(10081, ChronoUnit.MINUTES).toString()), "end", List.of()),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportErrors(graph))
                .containsExactly("node wait type WAIT_UNTIL requires configuration.at or configuration.until as an ISO-8601 instant no more than 10080 minutes in the future");
    }

    @Test
    void validateRuntimeSupportedAcceptsImplementedRuntimeSubset() {
        WorkflowGraphDto graph = graph(
                "entry",
                Map.of(
                        "entry", node("entry", "ENTRY_TRIGGER", Map.of(), "send", List.of()),
                        "send", node("send", "SEND_EMAIL", Map.of("campaignId", "campaign-1"), "delay", List.of()),
                        "delay", node("delay", "DELAY", Map.of("minutes", 5), "wait", List.of()),
                        "wait", node("wait", "WAIT_UNTIL", Map.of("at", Instant.now().plus(30, ChronoUnit.MINUTES).toString()), "condition", List.of()),
                        "condition", node("condition", "CONDITION", Map.of(), null,
                                List.of(new WorkflowGraphDto.ConditionEdge("hasOpened == true", "end"))),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportErrors(graph)).isEmpty();
    }

    @Test
    void validateRuntimeSupportedAcceptsBranchAndSplitConditionAliases() {
        WorkflowGraphDto graph = graph(
                "branch",
                Map.of(
                        "branch", node("branch", "BRANCH", Map.of(), "end",
                                List.of(new WorkflowGraphDto.ConditionEdge("hasOpened == true", "split"))),
                        "split", node("split", "SPLIT", Map.of(), "end",
                                List.of(new WorkflowGraphDto.ConditionEdge("score >= 50", "end"))),
                        "end", node("end", "END", Map.of(), null, List.of())
                ));

        assertThat(validator.runtimeSupportedNodeTypes()).contains("BRANCH", "SPLIT");
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
