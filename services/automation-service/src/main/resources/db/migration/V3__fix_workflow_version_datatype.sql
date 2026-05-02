-- V3: Fix workflows.version datatype from INT to BIGINT
-- Fixes: Schema-validation: wrong column type in [workflows.version]; found [INTEGER], expected [BIGINT]
-- The Workflow entity extends BaseEntity which has version as Long (BIGINT)

ALTER TABLE workflows ALTER COLUMN version TYPE BIGINT;
