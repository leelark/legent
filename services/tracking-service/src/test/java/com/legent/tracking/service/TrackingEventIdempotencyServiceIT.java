package com.legent.tracking.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.legent.tracking.service.TrackingEventIdempotencyService.ClaimStatus.CLAIMED;
import static com.legent.tracking.service.TrackingEventIdempotencyService.ClaimStatus.IN_PROGRESS;
import static com.legent.tracking.service.TrackingEventIdempotencyService.ClaimStatus.PROCESSED;
import static com.legent.tracking.service.TrackingEventIdempotencyService.ClaimStatus.RAW_WRITTEN;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TrackingEventIdempotencyService.class)
class TrackingEventIdempotencyServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("tracking_idempotency_test")
            .withUsername("tracking")
            .withPassword("tracking");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TrackingEventIdempotencyService service;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Test
    void claimForProcessing_whenNewClaim_returnsClaimedAndFreshDuplicateIsInProgress() {
        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-new", "idem-new"))
                .isEqualTo(CLAIMED);

        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-new", "idem-new"))
                .isEqualTo(IN_PROGRESS);
    }

    @Test
    void releaseClaim_deletesOnlyClaimsThatHaveNotReachedRawWrite() {
        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-release", "idem-release"))
                .isEqualTo(CLAIMED);

        service.releaseClaim("tenant-1", "workspace-1", "OPEN", "event-release", "idem-release");

        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-release", "idem-release"))
                .isEqualTo(CLAIMED);
    }

    @Test
    void markRawWritten_preservesClaimOnReleaseAndReportsRawWritten() {
        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-raw", "idem-raw"))
                .isEqualTo(CLAIMED);

        service.markRawWritten("tenant-1", "workspace-1", "OPEN", "event-raw", "idem-raw");
        service.releaseClaim("tenant-1", "workspace-1", "OPEN", "event-raw", "idem-raw");

        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-raw", "idem-raw"))
                .isEqualTo(RAW_WRITTEN);
    }

    @Test
    void markProcessed_backfillsRawWrittenTimestampAndReportsProcessed() {
        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "CLICK", "event-processed", "idem-processed"))
                .isEqualTo(CLAIMED);

        service.markProcessed("tenant-1", "workspace-1", "CLICK", "event-processed", "idem-processed");

        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "CLICK", "event-processed", "idem-processed"))
                .isEqualTo(PROCESSED);
    }

    @Test
    void claimForProcessing_whenInProgressClaimIsStale_reclaimsIt() {
        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-stale", "idem-stale"))
                .isEqualTo(CLAIMED);
        entityManager.createNativeQuery("""
                        UPDATE tracking_event_idempotency
                        SET updated_at = NOW() - INTERVAL '20 minutes'
                        WHERE tenant_id = ? AND workspace_id = ? AND event_type = ? AND event_id = ?
                        """)
                .setParameter(1, "tenant-1")
                .setParameter(2, "workspace-1")
                .setParameter(3, "OPEN")
                .setParameter(4, "event-stale")
                .executeUpdate();

        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-stale", "idem-stale"))
                .isEqualTo(CLAIMED);
    }

    @Test
    void markRawWritten_whenEventIdDiffersButKeyMatches_updatesMatchedKeyRow() {
        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-key-raw", "idem-key-raw"))
                .isEqualTo(CLAIMED);

        service.markRawWritten("tenant-1", "workspace-1", "OPEN", "event-different-raw", "idem-key-raw");

        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-different-raw", "idem-key-raw"))
                .isEqualTo(RAW_WRITTEN);
    }

    @Test
    void markProcessed_whenEventIdDiffersButKeyMatches_updatesMatchedKeyRow() {
        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-key-processed", "idem-key-processed"))
                .isEqualTo(CLAIMED);
        service.markRawWritten("tenant-1", "workspace-1", "OPEN", "event-key-processed", "idem-key-processed");

        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-different-processed", "idem-key-processed"))
                .isEqualTo(RAW_WRITTEN);

        service.markProcessed("tenant-1", "workspace-1", "OPEN", "event-different-processed", "idem-key-processed");

        assertThat(service.claimForProcessing("tenant-1", "workspace-1", "OPEN", "event-different-processed", "idem-key-processed"))
                .isEqualTo(PROCESSED);
    }
}
