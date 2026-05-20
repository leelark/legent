package com.legent.delivery.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpProviderWorkspaceMigrationTest {

    @Test
    void migration_addsProviderRoutingAndPoolWorkspaceScope() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V16__smtp_provider_workspace_scope.sql")) {
            assertTrue(stream != null, "V16 migration must be on the test classpath");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("ALTER TABLE smtp_providers"));
            assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS workspace_id"));
            assertTrue(sql.contains("chk_smtp_providers_workspace_present"));
            assertTrue(sql.contains("idx_smtp_providers_workspace_active"));
            assertTrue(sql.contains("uq_smtp_providers_tenant_workspace_id"));
            assertTrue(sql.contains("ALTER TABLE routing_rules"));
            assertTrue(sql.contains("uq_routing_domain_workspace"));
            assertTrue(sql.contains("lower(sender_domain)"));
            assertTrue(sql.contains("fk_routing_rules_provider_workspace"));
            assertTrue(sql.contains("fk_routing_rules_fallback_provider_workspace"));
            assertTrue(sql.contains("fk_routing_rules_ip_pool_workspace"));
            assertTrue(sql.contains("ALTER TABLE ip_pools"));
            assertTrue(sql.contains("uq_ip_pools_tenant_workspace_id"));
            assertTrue(sql.contains("chk_provider_scores_workspace_present"));
            assertTrue(sql.contains("uq_provider_score_workspace"));
            assertTrue(sql.contains("fk_provider_scores_provider_workspace"));
            assertTrue(sql.contains("fk_provider_health_checks_provider_workspace"));
            assertTrue(sql.contains("fk_provider_health_status_provider_workspace"));
            assertTrue(sql.contains("fk_provider_capacity_provider_workspace"));
            assertTrue(sql.contains("fk_provider_capacity_failover_workspace"));
            assertTrue(sql.contains("fk_provider_failover_primary_workspace"));
            assertTrue(sql.contains("fk_provider_failover_failover_workspace"));
        }
    }
}
