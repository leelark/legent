package com.legent.automation.dto;

import java.util.List;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowGraphDto {
    @Builder.Default
    private Integer graphVersion = 2;
    private String initialNodeId;
    private Map<String, WorkflowNode> nodes;
    private List<WorkflowEdge> edges;
    private EntryPolicy entryPolicy;
    private ReentryPolicy reentryPolicy;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowNode {
        private String id;
        private String type; // ENTRY_TRIGGER, SEND_EMAIL, DELAY, CONDITION, BRANCH, SPLIT, JOIN, WEBHOOK, UPDATE_FIELD, ADD_TAG, REMOVE_TAG, SUPPRESS_CONTACT, WAIT_UNTIL, PAUSE, EXIT_GOAL, REENTRY_GATE, EVENT_LISTENER, END
        private Map<String, Object> configuration; // e.g., delayMinutes: 60, campaignId: xyz
        private String nextNodeId; // For linear steps
        private List<ConditionEdge> branches; // For conditions
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionEdge {
        private String condition; // e.g., "opened == true"
        private String targetNodeId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowEdge {
        private String sourceNodeId;
        private String targetNodeId;
        private String edgeType; // DEFAULT, CONDITION, TRUE, FALSE, SPLIT
        private String condition;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntryPolicy {
        private boolean requireConsent;
        private boolean checkSuppression = true;
        private Integer frequencyCapPerDay;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReentryPolicy {
        private boolean allowReentry;
        private Integer cooldownMinutes;
    }
}
