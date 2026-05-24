package com.legent.audience.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DataExtensionRelationshipMigrationTest {

    @Test
    void relationshipMigrationAddsScopedContractTableAndIndexes() throws IOException {
        String sql = migrationSql("V21__data_extension_relationships.sql");

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS data_extension_relationships");
        assertThat(sql).contains("tenant_id                   VARCHAR(36) NOT NULL");
        assertThat(sql).contains("workspace_id                VARCHAR(36) NOT NULL");
        assertThat(sql).contains("source_data_extension_id    VARCHAR(36) NOT NULL REFERENCES data_extensions(id)");
        assertThat(sql).contains("target_data_extension_id    VARCHAR(36) NOT NULL REFERENCES data_extensions(id)");
        assertThat(sql).contains("chk_de_relationship_cardinality");
        assertThat(sql).contains("ONE_TO_ONE");
        assertThat(sql).contains("MANY_TO_MANY");
        assertThat(sql).contains("uq_de_relationship_name_active");
        assertThat(sql).contains("idx_de_relationship_source");
        assertThat(sql).contains("idx_de_relationship_target");
    }

    private String migrationSql(String fileName) throws IOException {
        Path repoRootPath = Path.of("services", "audience-service", "src", "main", "resources", "db", "migration", fileName);
        if (Files.exists(repoRootPath)) {
            return Files.readString(repoRootPath);
        }
        return Files.readString(Path.of("src", "main", "resources", "db", "migration", fileName));
    }
}
