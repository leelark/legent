package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import org.springframework.stereotype.Component;

@Component
public class EntryTriggerNodeHandler implements NodeHandler {

    @Override
    public String execute(WorkflowInstance instance, WorkflowGraphDto.WorkflowNode node) {
        return node.getNextNodeId();
    }

    @Override
    public String getType() {
        return "ENTRY_TRIGGER";
    }
}
