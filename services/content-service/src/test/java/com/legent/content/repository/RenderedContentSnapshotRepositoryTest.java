package com.legent.content.repository;

import com.legent.content.domain.RenderedContentSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RenderedContentSnapshotRepositoryTest {

    @Autowired
    private RenderedContentSnapshotRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:content_repository;"
                + "MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;"
                + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    }

    @Test
    void repositorySliceCreatesJsonbMappedTables() {
        Integer assetCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM assets", Integer.class);

        assertThat(assetCount).isZero();
    }

    @Test
    void deleteExpiredRunsInsideRepositoryTransaction() {
        RenderedContentSnapshot expired = repository.save(snapshot("expired", Instant.now().minusSeconds(60)));
        RenderedContentSnapshot live = repository.save(snapshot("live", Instant.now().plusSeconds(3600)));
        String expiredId = expired.getId();
        String liveId = live.getId();

        int deleted = repository.deleteExpired(Instant.now());

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(expiredId)).isEmpty();
        assertThat(repository.findById(liveId)).isPresent();
    }

    private RenderedContentSnapshot snapshot(String suffix, Instant expiresAt) {
        RenderedContentSnapshot snapshot = new RenderedContentSnapshot();
        snapshot.setTenantId("tenant-1");
        snapshot.setWorkspaceId("workspace-1");
        snapshot.setReferenceId("cr_" + suffix);
        snapshot.setCampaignId("campaign-1");
        snapshot.setJobId("job-1");
        snapshot.setBatchId("batch-1");
        snapshot.setMessageId("message-" + suffix);
        snapshot.setContentId("content-1");
        snapshot.setSubject("Rendered");
        snapshot.setHtmlBody("<p>Hello</p>");
        snapshot.setSubjectSha256("a".repeat(64));
        snapshot.setHtmlSha256("b".repeat(64));
        snapshot.setSubjectBytes(8);
        snapshot.setHtmlBytes(12);
        snapshot.setTextBytes(0);
        snapshot.setInlineFallbackIncluded(false);
        snapshot.setExpiresAt(expiresAt);
        return snapshot;
    }
}
