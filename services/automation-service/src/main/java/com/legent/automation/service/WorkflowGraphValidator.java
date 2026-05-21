package com.legent.automation.service;

import com.legent.automation.dto.WorkflowGraphDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final Set<String> RUNTIME_SUPPORTED_NODE_TYPES = Set.of(
            "ENTRY_TRIGGER",
            "SEND_EMAIL",
            "DELAY",
            "CONDITION",
            "END"
    );
    private static final Set<String> SEND_EMAIL_FORBIDDEN_NORMALIZED_CONFIG_KEYS = Set.of(
            "recipientemail",
            "email",
            "subscriberid",
            "contactid",
            "recipients",
            "audienceids",
            "audienceoverride",
            "contentid",
            "templateid",
            "senderemail",
            "providerid",
            "sendingdomain",
            "sendgovernancepolicyid",
            "skipsuppression",
            "skipwarmup",
            "ignoreratelimits",
            "forcesend"
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

    public void validateRuntimeSupported(WorkflowGraphDto graph) {
        List<String> errors = runtimeSupportErrors(graph);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Workflow graph uses unsupported runtime semantics: " + String.join("; ", errors));
        }
    }

    public Set<String> runtimeSupportedNodeTypes() {
        return RUNTIME_SUPPORTED_NODE_TYPES;
    }

    public boolean isRuntimeSupportedNodeType(String nodeType) {
        return RUNTIME_SUPPORTED_NODE_TYPES.contains(nodeType);
    }

    public List<String> runtimeSupportErrors(WorkflowGraphDto graph) {
        WorkflowGraphDto normalized = validateAndNormalize(graph);
        List<String> errors = new ArrayList<>();
        for (WorkflowGraphDto.WorkflowNode node : normalized.getNodes().values()) {
            String type = node.getType();
            if (!RUNTIME_SUPPORTED_NODE_TYPES.contains(type)) {
                errors.add("node " + node.getId() + " type " + type + " is not supported by the live workflow runtime");
                continue;
            }
            if ("SEND_EMAIL".equals(type)) {
                if (isBlank(node.getConfiguration().get("campaignId"))) {
                    errors.add("node " + node.getId() + " type SEND_EMAIL requires configuration.campaignId");
                }
                List<String> forbiddenKeys = node.getConfiguration().keySet().stream()
                        .filter(key -> key != null
                                && SEND_EMAIL_FORBIDDEN_NORMALIZED_CONFIG_KEYS.contains(normalizeConfigKey(key)))
                        .toList();
                if (!forbiddenKeys.isEmpty()) {
                    errors.add("node " + node.getId() + " type SEND_EMAIL cannot override campaign recipients, content, sender, provider, governance, or safety controls: "
                            + String.join(", ", forbiddenKeys));
                }
            }
            if ("DELAY".equals(type) && !validDelayMinutes(node.getConfiguration().get("minutes"))) {
                errors.add("node " + node.getId() + " type DELAY requires configuration.minutes between 1 and 10080");
            }
            if ("CONDITION".equals(type) && (node.getBranches() == null || node.getBranches().isEmpty())) {
                errors.add("node " + node.getId() + " type CONDITION requires at least one branch");
            }
        }
        return errors;
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private String normalizeConfigKey(String key) {
        return key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private boolean validDelayMinutes(Object value) {
        if (value == null) {
            return true;
        }
        int minutes;
        if (value instanceof Number number) {
            minutes = number.intValue();
        } else {
            try {
                minutes = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return minutes >= 1 && minutes <= 10080;
    }
}
