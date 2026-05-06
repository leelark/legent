package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import org.springframework.stereotype.Component;

/**
 * Fallback handler for declarative node types that don't need a dedicated runtime adapter.
 */
@Component
public class GenericNodeHandler implements NodeHandler {

    @Override
    public String execute(WorkflowInstance instance, WorkflowGraphDto.WorkflowNode node) {
        if (node.getBranches() != null && !node.getBranches().isEmpty()) {
            return node.getBranches().getFirst().getTargetNodeId();
        }
        return node.getNextNodeId();
    }

    @Override
    public String getType() {
        return "*";
    }
}
