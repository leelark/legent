package com.legent.audience.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactLifecycleAuditMigrationTest {

    @Test
    void contactLifecycleAuditMigrationCreatesScopedAppendOnlyAuditTableAndIndexes() throws IOException {
        String sql = migrationSql("V22__contact_lifecycle_audit.sql");

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS contact_lifecycle_audit");
        assertThat(sql).contains("tenant_id          VARCHAR(36) NOT NULL");
        assertThat(sql).contains("workspace_id       VARCHAR(36) NOT NULL");
        assertThat(sql).contains("subscriber_id      VARCHAR(36)");
        assertThat(sql).contains("data_extension_id  VARCHAR(36)");
        assertThat(sql).contains("email_sha256       CHAR(64)");
        assertThat(sql).contains("occurred_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()");
        assertThat(sql).contains("source_event_id    VARCHAR(120)");
        assertThat(sql).contains("metadata           JSONB NOT NULL DEFAULT '{}'::jsonb");
        assertThat(sql).contains("idx_contact_lifecycle_audit_scope");
        assertThat(sql).contains("idx_contact_lifecycle_audit_subject");
        assertThat(sql).contains("idx_contact_lifecycle_audit_subscriber");
        assertThat(sql).contains("idx_contact_lifecycle_audit_data_extension");
        assertThat(sql).contains("idx_contact_lifecycle_audit_email_hash");
        assertThat(sql).contains("idx_contact_lifecycle_audit_occurred_brin");
        assertThat(sql).contains("uq_contact_lifecycle_audit_source_event");
    }

    @Test
    void contactLifecycleAuditMigrationDefinesConstraintsWithoutRawEmailColumn() throws IOException {
        String sql = migrationSql("V22__contact_lifecycle_audit.sql");

        assertThat(sql).contains("chk_contact_lifecycle_subject_type");
        assertThat(sql).contains("SEND_ELIGIBILITY");
        assertThat(sql).contains("chk_contact_lifecycle_outcome");
        assertThat(sql).contains("chk_contact_lifecycle_email_hash");
        assertThat(sql).doesNotContain(" email ");
        assertThat(sql).doesNotContain("email_address");
    }

    private String migrationSql(String fileName) throws IOException {
        Path repoRootPath = Path.of("services", "audience-service", "src", "main", "resources", "db", "migration", fileName);
        if (Files.exists(repoRootPath)) {
            return Files.readString(repoRootPath);
        }
        return Files.readString(Path.of("src", "main", "resources", "db", "migration", fileName));
    }
}
