package com.legent.audience.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DataExtensionGovernanceMigrationTest {

    @Test
    void governanceMetadataMigrationAddsScopedAuditAndClassifications() throws IOException {
        String sql = migrationSql("V18__data_extension_governance_metadata.sql");

        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_type VARCHAR(32)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS data_classification VARCHAR(32)");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS data_classification VARCHAR(32) NOT NULL DEFAULT 'INTERNAL'");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS data_extension_governance_audit");
        assertThat(sql).contains("tenant_id           VARCHAR(36) NOT NULL");
        assertThat(sql).contains("workspace_id        VARCHAR(36) NOT NULL");
        assertThat(sql).contains("data_extension_id   VARCHAR(36) NOT NULL REFERENCES data_extensions(id)");
        assertThat(sql).contains("idx_de_governance_audit_scope");
        assertThat(sql).contains("chk_de_source_type");
        assertThat(sql).contains("chk_def_data_classification");
    }

    private String migrationSql(String fileName) throws IOException {
        Path repoRootPath = Path.of("services", "audience-service", "src", "main", "resources", "db", "migration", fileName);
        if (Files.exists(repoRootPath)) {
            return Files.readString(repoRootPath);
        }
        return Files.readString(Path.of("src", "main", "resources", "db", "migration", fileName));
    }
}
