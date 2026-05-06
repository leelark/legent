package com.legent.automation.service;

import com.legent.automation.dto.WorkflowGraphDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowGraphValidator {

    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of(
            "ENTRY_TRIGGER",
            "SEND_EMAIL",
            "DELAY",
            "CONDITION",
            "BRANCH",
            "SPLIT",
            "JOIN",
            "WEBHOOK",
            "UPDATE_FIELD",
            "ADD_TAG",
            "REMOVE_TAG",
            "SUPPRESS_CONTACT",
            "WAIT_UNTIL",
            "PAUSE",
            "EXIT_GOAL",
            "REENTRY_GATE",
            "EVENT_LISTENER",
            "END"
    );

    public WorkflowGraphDto validateAndNormalize(WorkflowGraphDto graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Workflow graph is required");
        }
        if (graph.getGraphVersion() == null) {
            graph.setGraphVersion(2);
        } else if (graph.getGraphVersion() != 2) {
            throw new IllegalArgumentException("Unsupported graphVersion. Expected 2");
        }
        if (graph.getInitialNodeId() == null || graph.getInitialNodeId().isBlank()) {
            throw new IllegalArgumentException("initialNodeId is required");
        }
        Map<String, WorkflowGraphDto.WorkflowNode> nodes = graph.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow graph must contain nodes");
        }
        if (!nodes.containsKey(graph.getInitialNodeId())) {
            throw new IllegalArgumentException("initialNodeId must reference an existing node");
        }

        for (Map.Entry<String, WorkflowGraphDto.WorkflowNode> entry : nodes.entrySet()) {
            String nodeKey = entry.getKey();
            WorkflowGraphDto.WorkflowNode node = entry.getValue();
            if (node == null) {
                throw new IllegalArgumentException("Node " + nodeKey + " is null");
            }
            if (node.getId() == null || node.getId().isBlank()) {
                node.setId(nodeKey);
            }
            if (!nodeKey.equals(node.getId())) {
                throw new IllegalArgumentException("Node key/id mismatch for node " + nodeKey);
            }
            if (node.getType() == null || node.getType().isBlank()) {
                throw new IllegalArgumentException("Node type missing for node " + nodeKey);
            }
            if (!SUPPORTED_NODE_TYPES.contains(node.getType())) {
                throw new IllegalArgumentException("Unsupported node type: " + node.getType());
            }
            if (node.getBranches() == null) {
                node.setBranches(new ArrayList<>());
            }
            if (node.getConfiguration() == null) {
                node.setConfiguration(Map.of());
            }
        }

        // Graph v2 edges may be provided in addition to inline nextNodeId/branches.
        List<WorkflowGraphDto.WorkflowEdge> edges = graph.getEdges();
        if (edges != null) {
            for (WorkflowGraphDto.WorkflowEdge edge : edges) {
                if (edge == null) {
                    continue;
                }
                WorkflowGraphDto.WorkflowNode source = nodes.get(edge.getSourceNodeId());
                WorkflowGraphDto.WorkflowNode target = nodes.get(edge.getTargetNodeId());
                if (source == null || target == null) {
                    throw new IllegalArgumentException("Edge references unknown source or target node");
                }

                String condition = edge.getCondition();
                String edgeType = edge.getEdgeType() == null ? "DEFAULT" : edge.getEdgeType().trim().toUpperCase();
                if ("CONDITION".equals(edgeType) || "TRUE".equals(edgeType) || "FALSE".equals(edgeType) || (condition != null && !condition.isBlank())) {
                    List<WorkflowGraphDto.ConditionEdge> branches = source.getBranches();
                    if (branches == null) {
                        branches = new ArrayList<>();
                        source.setBranches(branches);
                    }
                    String resolvedCondition = (condition == null || condition.isBlank()) ? edgeType.toLowerCase() : condition;
                    branches.add(new WorkflowGraphDto.ConditionEdge(resolvedCondition, edge.getTargetNodeId()));
                } else if (source.getNextNodeId() == null || source.getNextNodeId().isBlank()) {
                    source.setNextNodeId(edge.getTargetNodeId());
                }
            }
        }

        // Ensure referenced nodes from inline pointers exist.
        Set<String> danglingReferences = new LinkedHashSet<>();
        for (WorkflowGraphDto.WorkflowNode node : nodes.values()) {
            if (node.getNextNodeId() != null && !node.getNextNodeId().isBlank() && !nodes.containsKey(node.getNextNodeId())) {
                danglingReferences.add(node.getId() + "->" + node.getNextNodeId());
            }
            if (node.getBranches() != null) {
                for (WorkflowGraphDto.ConditionEdge branch : node.getBranches()) {
                    if (branch.getTargetNodeId() == null || !nodes.containsKey(branch.getTargetNodeId())) {
                        danglingReferences.add(node.getId() + "->" + branch.getTargetNodeId());
                    }
                }
            }
        }
        if (!danglingReferences.isEmpty()) {
            throw new IllegalArgumentException("Dangling node references: " + String.join(", ", danglingReferences));
        }

        return graph;
    }
}
