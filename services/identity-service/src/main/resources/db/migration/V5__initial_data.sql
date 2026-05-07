-- V5: Initial Data for Identity Service
-- Default Tenant
INSERT INTO tenants (id, name, status, settings, created_at, updated_at)
VALUES ('01HTENANT000000000000000001', 'Default Tenant', 'ACTIVE', '{}', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Admin users must be provisioned by an operator-controlled bootstrap or IdP flow.
-- Do not seed reusable credentials in migrations.
