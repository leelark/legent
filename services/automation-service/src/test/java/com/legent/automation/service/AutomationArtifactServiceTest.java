package com.legent.automation.service;

import com.legent.automation.domain.AutomationArtifact;
import com.legent.automation.dto.AutomationStudioDto;
import com.legent.automation.repository.AutomationArtifactRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationArtifactServiceTest {

    @Mock private AutomationArtifactRepository artifactRepository;

    private AutomationArtifactService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        service = new AutomationArtifactService(artifactRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createArtifactMintsWorkspaceScopedObjectKey() {
        when(artifactRepository.save(any(AutomationArtifact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.ArtifactResponse response = service.createArtifact(AutomationStudioDto.ArtifactRequest.builder()
                .displayName("import.csv")
                .contentType("text/csv; charset=utf-8")
                .sizeBytes(128L)
                .sha256("a".repeat(64))
                .build());

        assertThat(response.getArtifactId()).isNotBlank();
        assertThat(response.getContentType()).isEqualTo("text/csv");
        assertThat(response.getSha256()).isEqualTo("a".repeat(64));
    }

    @Test
    void createArtifactRejectsRawUrlsAndTraversal() {
        assertThatThrownBy(() -> service.createArtifact(AutomationStudioDto.ArtifactRequest.builder()
                .displayName("https://example.com/import.csv")
                .contentType("text/csv")
                .sizeBytes(128L)
                .sha256("a".repeat(64))
                .build()))
                .hasMessageContaining("safe CSV file name");

        assertThatThrownBy(() -> service.createArtifact(AutomationStudioDto.ArtifactRequest.builder()
                .displayName("../import.csv")
                .contentType("text/csv")
                .sizeBytes(128L)
                .sha256("a".repeat(64))
                .build()))
                .hasMessageContaining("safe CSV file name");
    }

    @Test
    void requireImportArtifactFailsClosedForCrossWorkspaceArtifactId() {
        when(artifactRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("artifact-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireImportArtifact("artifact-1"))
                .hasMessageContaining("current workspace");
    }

    @Test
    void requireImportArtifactRejectsExpiredArtifact() {
        AutomationArtifact artifact = validArtifact("artifact-1");
        artifact.setExpiresAt(Instant.now().minusSeconds(60));
        when(artifactRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("artifact-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(artifact));

        assertThatThrownBy(() -> service.requireImportArtifact("artifact-1"))
                .hasMessageContaining("expired");
    }

    private AutomationArtifact validArtifact(String artifactId) {
        AutomationArtifact artifact = new AutomationArtifact();
        artifact.setId(artifactId);
        artifact.setTenantId("tenant-1");
        artifact.setWorkspaceId("workspace-1");
        artifact.setSourceKind(AutomationArtifact.SourceKind.UPLOAD);
        artifact.setStatus(AutomationArtifact.ArtifactStatus.READY);
        artifact.setObjectKey("tenants/tenant-1/workspaces/workspace-1/automation-artifacts/" + artifactId + "/import.csv");
        artifact.setDisplayName("import.csv");
        artifact.setContentType("text/csv");
        artifact.setSizeBytes(128L);
        artifact.setSha256("a".repeat(64));
        artifact.setRetentionPolicy("AUTOMATION_30_DAYS");
        return artifact;
    }
}
