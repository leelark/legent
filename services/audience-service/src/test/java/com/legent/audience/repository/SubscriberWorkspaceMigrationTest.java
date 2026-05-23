package com.legent.audience.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriberWorkspaceMigrationTest {

    @Test
    void subscriberKeyWorkspaceMigrationUsesActiveOnlyPartialIndex() throws IOException {
        String sql = migrationSql("V19__workspace_scope_subscriber_key_uniqueness.sql");

        assertThat(sql).contains("DROP CONSTRAINT IF EXISTS uq_subscriber_tenant_key");
        assertThat(sql).contains("DROP CONSTRAINT IF EXISTS uk_subscriber_tenant_key");
        assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriber_tenant_workspace_key_active");
        assertThat(sql).contains("ON subscribers (tenant_id, workspace_id, subscriber_key)");
        assertThat(sql).contains("WHERE deleted_at IS NULL");
    }

    private String migrationSql(String fileName) throws IOException {
        Path repoRootPath = Path.of("services", "audience-service", "src", "main", "resources", "db", "migration", fileName);
        if (Files.exists(repoRootPath)) {
            return Files.readString(repoRootPath);
        }
        return Files.readString(Path.of("src", "main", "resources", "db", "migration", fileName));
    }
}
