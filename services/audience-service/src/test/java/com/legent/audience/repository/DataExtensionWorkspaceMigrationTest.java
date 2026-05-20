package com.legent.audience.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DataExtensionWorkspaceMigrationTest {

    @Test
    void workspaceScopeMigrationBackfillsAndGuardsRecordOwnership() throws IOException {
        String sql = migrationSql("V16__workspace_scope_data_extensions.sql");

        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36)");
        assertThat(sql).contains("SET workspace_id = COALESCE(workspace_id, 'workspace-default')");
        assertThat(sql).contains("RAISE EXCEPTION 'Cannot create uq_de_tenant_workspace_name_active");
        assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_de_tenant_workspace_name_active");
        assertThat(sql).contains("ON data_extensions (tenant_id, workspace_id, lower(name))");
        assertThat(sql).contains("CREATE INDEX IF NOT EXISTS idx_der_tenant_workspace_de_created");
        assertThat(sql).contains("der.tenant_id <> de.tenant_id");
        assertThat(sql).contains("der.workspace_id <> de.workspace_id");
        assertThat(sql).contains("RAISE EXCEPTION 'Cannot workspace-scope data_extension_records");
    }

    @Test
    void legacyWorkspaceMappingMigrationFailsClosedWithoutReviewedMapping() throws IOException {
        String sql = migrationSql("V17__guard_data_extension_workspace_mapping.sql");

        assertThat(sql).contains("to_regclass('public.audience_data_extension_workspace_mapping_review')");
        assertThat(sql).contains("Legacy data_extensions require reviewed workspace mapping before release");
        assertThat(sql).contains("HAVING COUNT(*) > 1");
        assertThat(sql).contains("Legacy data_extension workspace mapping contains duplicate tenant_id/data_extension_id rows");
        assertThat(sql).contains("target_workspace_id");
        assertThat(sql).contains("reviewed_by");
        assertThat(sql).contains("reviewed_at");
        assertThat(sql).contains("TRIM(map.target_workspace_id) = 'workspace-default'");
        assertThat(sql).contains("would create duplicate active names per tenant/workspace");
        assertThat(sql).contains("UPDATE data_extension_records der");
        assertThat(sql).contains("der.workspace_id <> de.workspace_id");
        assertThat(sql).contains("left records out of sync with parent data extensions");
    }

    private String migrationSql(String fileName) throws IOException {
        Path repoRootPath = Path.of("services", "audience-service", "src", "main", "resources", "db", "migration", fileName);
        if (Files.exists(repoRootPath)) {
            return Files.readString(repoRootPath);
        }
        return Files.readString(Path.of("src", "main", "resources", "db", "migration", fileName));
    }
}
