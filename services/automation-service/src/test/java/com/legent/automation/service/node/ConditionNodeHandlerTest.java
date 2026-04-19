package com.legent.automation.service.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConditionNodeHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConditionNodeHandler handler = new ConditionNodeHandler(objectMapper);

    @Test
    void execute_legacyTruePathCondition_returnsTruePathTarget() throws Exception {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setContext(objectMapper.writeValueAsString(Map.of("hasOpened", true)));

        WorkflowGraphDto.WorkflowNode node = new WorkflowGraphDto.WorkflowNode();
        node.setNextNodeId("fallback-node");
        node.setBranches(List.of(
                new WorkflowGraphDto.ConditionEdge("true_path", "opened-node"),
                new WorkflowGraphDto.ConditionEdge("false_path", "not-opened-node")
        ));

        String nextNode = handler.execute(instance, node);

        assertEquals("opened-node", nextNode);
    }

    @Test
    void execute_expressionBranching_returnsMatchingBranch() throws Exception {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setContext(objectMapper.writeValueAsString(Map.of(
                "opens", 1,
                "country", "US"
        )));

        WorkflowGraphDto.WorkflowNode node = new WorkflowGraphDto.WorkflowNode();
        node.setNextNodeId("fallback-node");
        node.setBranches(List.of(
                new WorkflowGraphDto.ConditionEdge("opens >= 3", "engaged-node"),
                new WorkflowGraphDto.ConditionEdge("country == 'US'", "us-node")
        ));

        String nextNode = handler.execute(instance, node);

        assertEquals("us-node", nextNode);
    }

    @Test
    void execute_whenNoBranchMatches_fallsBackToNextNode() throws Exception {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setContext(objectMapper.writeValueAsString(Map.of("opens", 0)));

        WorkflowGraphDto.WorkflowNode node = new WorkflowGraphDto.WorkflowNode();
        node.setNextNodeId("fallback-node");
        node.setBranches(List.of(
                new WorkflowGraphDto.ConditionEdge("opens > 5", "high-opens-node")
        ));

        String nextNode = handler.execute(instance, node);

        assertEquals("fallback-node", nextNode);
    }
}
