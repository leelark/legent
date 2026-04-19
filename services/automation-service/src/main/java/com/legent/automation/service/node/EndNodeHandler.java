package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import org.springframework.stereotype.Component;

@Component
public class EndNodeHandler implements NodeHandler {
    @Override
    public String getType() {
        return "END";
    }

    @Override
    public String execute(WorkflowInstance instance, WorkflowGraphDto.WorkflowNode node) {
        // End node: no next node
        return null;
    }
}
