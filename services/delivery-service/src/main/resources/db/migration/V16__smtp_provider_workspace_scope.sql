-- V16: Scope delivery provider configuration and routing by workspace.
-- Existing tenant-only rows are backfilled from workspace-aware delivery data
-- when unambiguous, then fall back to the repository's legacy workspace-default
-- convention used by earlier delivery workspace migrations.

ALTER TABLE smtp_providers
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

UPDATE smtp_providers provider
SET workspace_id = scoped.workspace_id
FROM (
    SELECT tenant_id, provider_id, MIN(workspace_id) AS workspace_id
    FROM provider_health_status
    WHERE provider_id IS NOT NULL
      AND workspace_id IS NOT NULL
      AND workspace_id <> ''
    GROUP BY tenant_id, provider_id
    HAVING COUNT(DISTINCT workspace_id) = 1
) scoped
WHERE provider.tenant_id = scoped.tenant_id
  AND provider.id = scoped.provider_id
  AND (provider.workspace_id IS NULL OR provider.workspace_id = '');

UPDATE smtp_providers provider
SET workspace_id = scoped.workspace_id
FROM (
    SELECT tenant_id, provider_id, MIN(workspace_id) AS workspace_id
    FROM message_logs
    WHERE provider_id IS NOT NULL
      AND workspace_id IS NOT NULL
      AND workspace_id <> ''
    GROUP BY tenant_id, provider_id
    HAVING COUNT(DISTINCT workspace_id) = 1
) scoped
WHERE provider.tenant_id = scoped.tenant_id
  AND provider.id = scoped.provider_id
  AND (provider.workspace_id IS NULL OR provider.workspace_id = '');

UPDATE smtp_providers
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE smtp_providers
    ADD CONSTRAINT chk_smtp_providers_workspace_present
    CHECK (workspace_id IS NOT NULL AND workspace_id <> '') NOT VALID;
ALTER TABLE smtp_providers VALIDATE CONSTRAINT chk_smtp_providers_workspace_present;
ALTER TABLE smtp_providers
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_smtp_providers_tenant_workspace
    ON smtp_providers(tenant_id, workspace_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_smtp_providers_workspace_active
    ON smtp_providers(tenant_id, workspace_id, is_active, priority)
    WHERE deleted_at IS NULL;

ALTER TABLE smtp_providers
    ADD CONSTRAINT uq_smtp_providers_tenant_workspace_id
    UNIQUE (tenant_id, workspace_id, id);

ALTER TABLE routing_rules
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

UPDATE routing_rules rule
SET workspace_id = provider.workspace_id
FROM smtp_providers provider
WHERE rule.tenant_id = provider.tenant_id
  AND rule.provider_id = provider.id
  AND (rule.workspace_id IS NULL OR rule.workspace_id = '');

UPDATE routing_rules rule
SET workspace_id = provider.workspace_id
FROM smtp_providers provider
WHERE rule.tenant_id = provider.tenant_id
  AND rule.fallback_provider_id = provider.id
  AND (rule.workspace_id IS NULL OR rule.workspace_id = '');

UPDATE routing_rules
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE routing_rules
    ADD CONSTRAINT chk_routing_rules_workspace_present
    CHECK (workspace_id IS NOT NULL AND workspace_id <> '') NOT VALID;
ALTER TABLE routing_rules VALIDATE CONSTRAINT chk_routing_rules_workspace_present;
ALTER TABLE routing_rules
    ALTER COLUMN workspace_id SET NOT NULL;

DROP INDEX IF EXISTS uq_routing_domain;
CREATE UNIQUE INDEX IF NOT EXISTS uq_routing_domain_workspace
    ON routing_rules(tenant_id, workspace_id, lower(sender_domain))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_routing_rules_tenant_workspace
    ON routing_rules(tenant_id, workspace_id)
    WHERE deleted_at IS NULL;

ALTER TABLE ip_pools
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

UPDATE ip_pools pool
SET workspace_id = scoped.workspace_id
FROM (
    SELECT tenant_id, ip_pool_id, MIN(workspace_id) AS workspace_id
    FROM routing_rules
    WHERE ip_pool_id IS NOT NULL
      AND workspace_id IS NOT NULL
      AND workspace_id <> ''
    GROUP BY tenant_id, ip_pool_id
    HAVING COUNT(DISTINCT workspace_id) = 1
) scoped
WHERE pool.tenant_id = scoped.tenant_id
  AND pool.id = scoped.ip_pool_id
  AND (pool.workspace_id IS NULL OR pool.workspace_id = '');

UPDATE ip_pools
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE ip_pools
    ADD CONSTRAINT chk_ip_pools_workspace_present
    CHECK (workspace_id IS NOT NULL AND workspace_id <> '') NOT VALID;
ALTER TABLE ip_pools VALIDATE CONSTRAINT chk_ip_pools_workspace_present;
ALTER TABLE ip_pools
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ip_pools_tenant_workspace
    ON ip_pools(tenant_id, workspace_id)
    WHERE deleted_at IS NULL;

ALTER TABLE ip_pools
    ADD CONSTRAINT uq_ip_pools_tenant_workspace_id
    UNIQUE (tenant_id, workspace_id, id);

ALTER TABLE routing_rules
    ADD CONSTRAINT fk_routing_rules_provider_workspace
    FOREIGN KEY (tenant_id, workspace_id, provider_id)
    REFERENCES smtp_providers(tenant_id, workspace_id, id);

ALTER TABLE routing_rules
    ADD CONSTRAINT fk_routing_rules_fallback_provider_workspace
    FOREIGN KEY (tenant_id, workspace_id, fallback_provider_id)
    REFERENCES smtp_providers(tenant_id, workspace_id, id);

ALTER TABLE routing_rules
    ADD CONSTRAINT fk_routing_rules_ip_pool_workspace
    FOREIGN KEY (tenant_id, workspace_id, ip_pool_id)
    REFERENCES ip_pools(tenant_id, workspace_id, id);

ALTER TABLE provider_scores
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

UPDATE provider_scores score
SET workspace_id = provider.workspace_id
FROM smtp_providers provider
WHERE score.tenant_id = provider.tenant_id
  AND score.provider_id = provider.id
  AND (score.workspace_id IS NULL OR score.workspace_id = '');

UPDATE provider_scores
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE provider_scores
    ADD CONSTRAINT chk_provider_scores_workspace_present
    CHECK (workspace_id IS NOT NULL AND workspace_id <> '') NOT VALID;
ALTER TABLE provider_scores VALIDATE CONSTRAINT chk_provider_scores_workspace_present;
ALTER TABLE provider_scores
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE provider_scores
    DROP CONSTRAINT IF EXISTS uq_provider_score;
ALTER TABLE provider_scores
    ADD CONSTRAINT uq_provider_score_workspace
    UNIQUE (tenant_id, workspace_id, provider_id, calculated_at);

CREATE INDEX IF NOT EXISTS idx_provider_scores_tenant_workspace_provider
    ON provider_scores(tenant_id, workspace_id, provider_id, calculated_at DESC);

ALTER TABLE provider_scores
    ADD CONSTRAINT fk_provider_scores_provider_workspace
    FOREIGN KEY (tenant_id, workspace_id, provider_id)
    REFERENCES smtp_providers(tenant_id, workspace_id, id);

ALTER TABLE provider_health_checks
    ADD CONSTRAINT fk_provider_health_checks_provider_workspace
    FOREIGN KEY (tenant_id, workspace_id, provider_id)
    REFERENCES smtp_providers(tenant_id, workspace_id, id);

ALTER TABLE provider_health_status
    ADD CONSTRAINT fk_provider_health_status_provider_workspace
    FOREIGN KEY (tenant_id, workspace_id, provider_id)
    REFERENCES smtp_providers(tenant_id, workspace_id, id);

ALTER TABLE provider_capacity_profiles
    ADD CONSTRAINT fk_provider_capacity_provider_workspace
    FOREIGN KEY (tenant_id, workspace_id, provider_id)
    REFERENCES smtp_providers(tenant_id, workspace_id, id);

ALTER TABLE provider_capacity_profiles
    ADD CONSTRAINT fk_provider_capacity_failover_workspace
    FOREIGN KEY (tenant_id, workspace_id, failover_provider_id)
    REFERENCES smtp_providers(tenant_id, workspace_id, id);

ALTER TABLE provider_failover_tests
    ADD CONSTRAINT fk_provider_failover_primary_workspace
    FOREIGN KEY (tenant_id, workspace_id, primary_provider_id)
    REFERENCES smtp_providers(tenant_id, workspace_id, id);

ALTER TABLE provider_failover_tests
    ADD CONSTRAINT fk_provider_failover_failover_workspace
    FOREIGN KEY (tenant_id, workspace_id, failover_provider_id)
    REFERENCES smtp_providers(tenant_id, workspace_id, id);
