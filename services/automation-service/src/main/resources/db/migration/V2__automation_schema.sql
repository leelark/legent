-- Add extensibility for node types and event logs

CREATE TABLE IF NOT EXISTS workflow_event_logs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    instance_id VARCHAR(36) NOT NULL,
    workflow_id VARCHAR(36) NOT NULL,
    node_id VARCHAR(255),
    node_type VARCHAR(50),
    event_type VARCHAR(50) NOT NULL, -- step.completed, started, failed, etc.
    payload JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_event_logs_instance ON workflow_event_logs(tenant_id, instance_id);
CREATE INDEX idx_event_logs_workflow ON workflow_event_logs(tenant_id, workflow_id);
