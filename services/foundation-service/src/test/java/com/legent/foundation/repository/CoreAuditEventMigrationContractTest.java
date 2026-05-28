package com.legent.foundation.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CoreAuditEventMigrationContractTest {

    @Test
    void coreAuditEventReferencesSupportTenantOwnedOrganizationIds() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("ALTER TABLE core_audit_events ALTER COLUMN workspace_id TYPE VARCHAR(64)")
                .contains("ALTER TABLE core_audit_events ALTER COLUMN environment_id TYPE VARCHAR(64)")
                .contains("ALTER TABLE core_audit_events ALTER COLUMN actor_id TYPE VARCHAR(64)")
                .contains("ALTER TABLE core_audit_events ALTER COLUMN resource_id TYPE VARCHAR(64)")
                .contains("ALTER TABLE core_audit_events ALTER COLUMN created_by TYPE VARCHAR(64)");
    }

    private String migrationSql() throws IOException {
        Path modulePath = Path.of("src", "main", "resources", "db", "migration",
                "V19__widen_core_audit_event_references.sql");
        if (Files.exists(modulePath)) {
            return Files.readString(modulePath);
        }
        return Files.readString(Path.of("services", "foundation-service", "src", "main", "resources",
                "db", "migration", "V19__widen_core_audit_event_references.sql"));
    }
}
