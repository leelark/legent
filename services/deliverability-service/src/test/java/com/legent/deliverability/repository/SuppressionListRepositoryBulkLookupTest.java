package com.legent.deliverability.repository;

import com.legent.deliverability.domain.SuppressionList;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SuppressionListRepositoryBulkLookupTest {

    @Autowired private SuppressionListRepository repository;

    @Test
    void bulkLookupIsTenantWorkspaceCandidateCaseAndExpiryScoped() {
        repository.save(suppression("match-1", "tenant-1", "workspace-1", " Mixed@Example.COM ", null));
        repository.save(suppression("expired-1", "tenant-1", "workspace-1", "expired@example.com", Instant.now().minusSeconds(60)));
        repository.save(suppression("future-1", "tenant-1", "workspace-1", "future@example.com", Instant.now().plusSeconds(60)));
        repository.save(suppression("tenant-2", "tenant-2", "workspace-1", "other-tenant@example.com", null));
        repository.save(suppression("workspace-2", "tenant-1", "workspace-2", "other-workspace@example.com", null));
        repository.save(suppression("non-candidate", "tenant-1", "workspace-1", "not-requested@example.com", null));
        repository.flush();

        List<String> matches = repository.findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
                "tenant-1",
                "workspace-1",
                List.of(
                        "mixed@example.com",
                        "expired@example.com",
                        "future@example.com",
                        "other-tenant@example.com",
                        "other-workspace@example.com"));

        assertThat(matches)
                .containsExactlyInAnyOrder(" Mixed@Example.COM ", "future@example.com");
    }

    private SuppressionList suppression(String id, String tenantId, String workspaceId, String email, Instant expiresAt) {
        SuppressionList suppression = new SuppressionList();
        suppression.setId(id);
        suppression.setTenantId(tenantId);
        suppression.setWorkspaceId(workspaceId);
        suppression.setEmail(email);
        suppression.setReason("HARD_BOUNCE");
        suppression.setSource("TEST");
        suppression.setOwnershipScope("WORKSPACE");
        suppression.setExpiresAt(expiresAt);
        return suppression;
    }
}
