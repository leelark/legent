package com.legent.delivery.repository;

import com.legent.delivery.domain.SmtpProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SmtpProviderRepositoryWorkspaceTest {

    @Autowired
    private SmtpProviderRepository repository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:delivery_provider_repository;"
                + "MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;"
                + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    }

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void activeLookup_returnsOnlyRequestedWorkspace() {
        repository.save(provider("provider-a", "tenant-1", "workspace-a", true, 2));
        repository.save(provider("provider-b", "tenant-1", "workspace-b", true, 1));
        repository.save(provider("provider-c", "tenant-1", "workspace-a", false, 0));

        List<SmtpProvider> providers = repository.findByTenantIdAndWorkspaceIdAndIsActiveTrueAndDeletedAtIsNullOrderByPriorityAsc(
                "tenant-1",
                "workspace-a");

        assertEquals(1, providers.size());
        assertEquals("provider-a", providers.getFirst().getId());
    }

    @Test
    void idLookup_deniesSameTenantSiblingWorkspaceProvider() {
        repository.save(provider("provider-a", "tenant-1", "workspace-a", true, 1));
        repository.save(provider("provider-b", "tenant-1", "workspace-b", true, 1));

        assertTrue(repository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                "provider-b",
                "tenant-1",
                "workspace-a").isEmpty());
    }

    private SmtpProvider provider(String id, String tenantId, String workspaceId, boolean active, int priority) {
        SmtpProvider provider = new SmtpProvider();
        provider.setId(id);
        provider.setTenantId(tenantId);
        provider.setWorkspaceId(workspaceId);
        provider.setName(id);
        provider.setType("SMTP");
        provider.setActive(active);
        provider.setPriority(priority);
        return provider;
    }
}
