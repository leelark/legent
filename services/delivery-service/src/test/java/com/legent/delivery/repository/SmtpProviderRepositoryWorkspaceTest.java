package com.legent.delivery.repository;

import com.legent.delivery.domain.SmtpProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SmtpProviderRepositoryWorkspaceTest {

    @Autowired
    private SmtpProviderRepository repository;

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
