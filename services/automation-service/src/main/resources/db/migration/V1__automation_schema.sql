-- V1 Automation Schema

CREATE TABLE workflows (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL, -- DRAFT, ACTIVE, PAUSED, ARCHIVED
    version INT DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    deleted_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_workflows_tenant ON workflows(tenant_id);

CREATE TABLE workflow_definitions (
    workflow_id VARCHAR(36) REFERENCES workflows(id) ON DELETE CASCADE,
    version INT NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    definition JSONB NOT NULL, -- nodes and edges JSON mapping
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(workflow_id, version)
);

CREATE TABLE workflow_instances (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    workflow_id VARCHAR(36) REFERENCES workflows(id),
    version INT NOT NULL,
    subscriber_id VARCHAR(36) NOT NULL,
    status VARCHAR(50) NOT NULL, -- RUNNING, WAITING, COMPLETED, FAILED
    current_node_id VARCHAR(255),
    context JSONB, -- internal state variables
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_instances_sub ON workflow_instances(tenant_id, subscriber_id);
CREATE INDEX idx_instances_flow ON workflow_instances(tenant_id, workflow_id, status);

CREATE TABLE instance_history (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    instance_id VARCHAR(36) REFERENCES workflow_instances(id) ON DELETE CASCADE,
    node_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL, -- SUCCESS, ERROR
    error_message TEXT,
    executed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(26),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 0
);

-- Note: In application.yml, spring.quartz.jdbc.initialize-schema is set to "always"
-- which will auto-inject the Quartz QRTZ_* tables dynamically via Spring Boot on startup.
-- No need to redefine them explicitly here.
