package com.legent.deliverability.repository;

import com.legent.deliverability.domain.ReputationScore;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReputationScoreRepositoryTest {

    @Autowired private ReputationScoreRepository repository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:deliverability_reputation_repository;"
                + "MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;"
                + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    }

    @Test
    void scopedLookupReturnsLatestScoreForTenantWorkspaceAndDomainOnly() {
        repository.save(score("tenant-1", "workspace-1", "example.com", 40, Instant.parse("2026-05-21T09:00:00Z")));
        repository.save(score("tenant-1", "workspace-1", "example.com", 68, Instant.parse("2026-05-21T10:00:00Z")));
        repository.save(score("tenant-1", "workspace-2", "example.com", 99, Instant.parse("2026-05-21T11:00:00Z")));
        repository.save(score("tenant-2", "workspace-1", "example.com", 97, Instant.parse("2026-05-21T11:30:00Z")));
        repository.save(score("tenant-1", "workspace-1", "other.com", 95, Instant.parse("2026-05-21T12:00:00Z")));
        repository.flush();

        ReputationScore result = repository.findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                "tenant-1", "workspace-1", "example.com");

        assertThat(result).isNotNull();
        assertThat(result.getScore()).isEqualTo(68);
        assertThat(result.getTenantId()).isEqualTo("tenant-1");
        assertThat(result.getWorkspaceId()).isEqualTo("workspace-1");
    }

    @Test
    void scopedLookupDoesNotReturnTenantOrWorkspaceMismatches() {
        repository.save(score("tenant-1", "workspace-2", "example.com", 99, Instant.parse("2026-05-21T11:00:00Z")));
        repository.save(score("tenant-2", "workspace-1", "example.com", 97, Instant.parse("2026-05-21T11:30:00Z")));
        repository.flush();

        ReputationScore result = repository.findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                "tenant-1", "workspace-1", "example.com");

        assertThat(result).isNull();
    }

    private ReputationScore score(String tenantId, String workspaceId, String domain, double score, Instant lastUpdated) {
        ReputationScore reputationScore = new ReputationScore();
        reputationScore.setTenantId(tenantId);
        reputationScore.setWorkspaceId(workspaceId);
        reputationScore.setDomain(domain);
        reputationScore.setScore(score);
        reputationScore.setLastUpdated(lastUpdated);
        return reputationScore;
    }
}
