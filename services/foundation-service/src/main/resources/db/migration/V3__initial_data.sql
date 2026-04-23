-- Initial Data for Foundation Service
-- Default Tenant (matches Identity Service)
INSERT INTO tenants (id, name, slug, status, plan, settings, created_at, updated_at, version)
VALUES ('01HTENANT000000000000000001', 'Default Tenant', 'default-tenant', 'ACTIVE', 'ENTERPRISE', '{}', NOW(), NOW(), 1)
ON CONFLICT (id) DO NOTHING;

-- Default System Configurations for the Tenant
INSERT INTO system_configs (id, tenant_id, config_key, config_value, value_type, category, description, is_system, version)
VALUES
    ('01HDEFAULTCONFIG0000000001', '01HTENANT000000000000000001', 'app.name', 'Legent Marketing Cloud', 'STRING', 'GENERAL', 'Application name', TRUE, 1),
    ('01HDEFAULTCONFIG0000000002', '01HTENANT000000000000000001', 'app.url', 'http://localhost:8080', 'STRING', 'GENERAL', 'Application base URL', TRUE, 1)
ON CONFLICT (id) DO NOTHING;
