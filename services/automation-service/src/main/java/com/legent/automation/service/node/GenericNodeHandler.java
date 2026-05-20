package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import org.springframework.stereotype.Component;

@Component
public class GenericNodeHandler implements NodeHandler {

    @Override
    public String execute(WorkflowInstance instance, WorkflowGraphDto.WorkflowNode node) {
        String type = node == null ? "UNKNOWN" : node.getType();
        throw new IllegalStateException("Workflow node type " + type + " is not supported by the live workflow runtime");
    }

    @Override
    public String getType() {
        return "*";
    }
}
