DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_users_tenant'
          AND table_name = 'users'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT fk_users_tenant
            FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_tenant_active
    ON users(tenant_id, is_active)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_tenant_role
    ON users(tenant_id, role)
    WHERE deleted_at IS NULL;
