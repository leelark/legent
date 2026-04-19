package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;

public interface NodeHandler {
    
    /**
     * Executes the logic for a specific node type.
     * 
     * @param instance the current subscriber workflow state
     * @param node the JSON node details
     * @return the ID of the next node to transition to, or NULL if waiting/suspended/finished.
     */
    String execute(WorkflowInstance instance, WorkflowGraphDto.WorkflowNode node);
    
    String getType();
}
