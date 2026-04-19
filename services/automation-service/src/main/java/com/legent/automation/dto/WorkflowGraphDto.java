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
    private String initialNodeId;
    private Map<String, WorkflowNode> nodes;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowNode {
        private String id;
        private String type; // TRIGGER, SEND_EMAIL, DELAY, CONDITION, END
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
}
