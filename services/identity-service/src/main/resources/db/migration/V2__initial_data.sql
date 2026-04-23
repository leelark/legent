-- Initial Data for Identity Service
-- Default Tenant
INSERT INTO tenants (id, name, status, settings, created_at, updated_at)
VALUES ('01HTENANT000000000000000001', 'Default Tenant', 'ACTIVE', '{}', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Default Admin User (password: Admin@123)
-- Hash generated for: Admin@123
INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, role, is_active, created_at, updated_at)
VALUES ('01HUSER00000000000000000001', '01HTENANT000000000000000001', 'admin@legent.com', '$2a$12$R9h/lIPzHZ7hJL1n1z8yOuK1k1k1k1k1k1k1k1k1k1k1k1k1k1k1', 'Admin', 'User', 'ADMIN', true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
