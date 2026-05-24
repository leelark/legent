-- V21: First-class Contact Builder style data-extension relationship metadata.

CREATE TABLE IF NOT EXISTS data_extension_relationships (
    id                          VARCHAR(36) PRIMARY KEY,
    tenant_id                   VARCHAR(36) NOT NULL,
    workspace_id                VARCHAR(36) NOT NULL,
    source_data_extension_id    VARCHAR(36) NOT NULL REFERENCES data_extensions(id),
    target_data_extension_id    VARCHAR(36) NOT NULL REFERENCES data_extensions(id),
    name                        VARCHAR(128) NOT NULL,
    source_field                VARCHAR(128) NOT NULL,
    target_field                VARCHAR(128) NOT NULL,
    cardinality                 VARCHAR(32) NOT NULL,
    is_required                 BOOLEAN NOT NULL DEFAULT FALSE,
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    ordinal                     INTEGER NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(36),
    deleted_at                  TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_de_relationship_cardinality
        CHECK (cardinality IN ('ONE_TO_ONE', 'ONE_TO_MANY', 'MANY_TO_ONE', 'MANY_TO_MANY'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_de_relationship_name_active
    ON data_extension_relationships (tenant_id, workspace_id, source_data_extension_id, lower(name))
    WHERE deleted_at IS NULL
      AND is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_de_relationship_source
    ON data_extension_relationships (tenant_id, workspace_id, source_data_extension_id, ordinal)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_de_relationship_target
    ON data_extension_relationships (tenant_id, workspace_id, target_data_extension_id)
    WHERE deleted_at IS NULL;
