package com.legent.content.service;

import com.legent.common.event.EmailContentReference;
import com.legent.content.domain.RenderedContentSnapshot;
import com.legent.content.repository.RenderedContentSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenderedContentSnapshotServiceTest {

    @Mock private RenderedContentSnapshotRepository repository;

    private RenderedContentSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new RenderedContentSnapshotService(repository);
        ReflectionTestUtils.setField(service, "snapshotTtl", Duration.ofHours(72));
    }

    @Test
    void createPersistsScopedSnapshotAndReturnsMetadataOnly() {
        when(repository.findByTenantIdAndWorkspaceIdAndReferenceIdAndDeletedAtIsNull(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(RenderedContentSnapshot.class))).thenAnswer(invocation -> {
            RenderedContentSnapshot snapshot = invocation.getArgument(0, RenderedContentSnapshot.class);
            snapshot.setId("snapshot-1");
            snapshot.setCreatedAt(Instant.now());
            return snapshot;
        });

        EmailContentReference reference = service.create("tenant-1", "workspace-1", request(), false);

        ArgumentCaptor<RenderedContentSnapshot> snapshotCaptor = ArgumentCaptor.forClass(RenderedContentSnapshot.class);
        verify(repository).save(snapshotCaptor.capture());
        RenderedContentSnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.getTenantId()).isEqualTo("tenant-1");
        assertThat(snapshot.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(snapshot.getReferenceId()).startsWith("cr_");
        assertThat(snapshot.getSubject()).isEqualTo("Rendered");
        assertThat(snapshot.getHtmlBody()).isEqualTo("<p>Hello</p>");
        assertThat(snapshot.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofHours(71)));
        assertThat(reference.getStorageBackend()).isEqualTo("content-service");
        assertThat(reference.getReferenceId()).isEqualTo(snapshot.getReferenceId());
        assertThat(reference.getHtmlSha256()).hasSize(64);
    }

    @Test
    void readReturnsOnlyLiveScopedSnapshot() {
        RenderedContentSnapshot snapshot = new RenderedContentSnapshot();
        snapshot.setTenantId("tenant-1");
        snapshot.setWorkspaceId("workspace-1");
        snapshot.setCampaignId("campaign-1");
        snapshot.setJobId("job-1");
        snapshot.setBatchId("batch-1");
        snapshot.setMessageId("message-1");
        snapshot.setContentId("content-1");
        snapshot.setReferenceId("cr_ref");
        snapshot.setSubject("Rendered");
        snapshot.setHtmlBody("<p>Hello</p>");
        snapshot.setTextBody("Hello");
        snapshot.setExpiresAt(Instant.now().plusSeconds(3600));
        when(repository.findByTenantIdAndWorkspaceIdAndReferenceIdAndDeletedAtIsNull("tenant-1", "workspace-1", "cr_ref"))
                .thenReturn(Optional.of(snapshot));

        RenderedContentSnapshotService.StoredRenderedContent content =
                service.read("tenant-1", "workspace-1", "cr_ref");

        assertThat(content.subject()).isEqualTo("Rendered");
        assertThat(content.htmlBody()).isEqualTo("<p>Hello</p>");
        assertThat(content.metadata()).containsEntry("tenantId", "tenant-1");
    }

    @Test
    void purgeExpiredSnapshotsDeletesExpiredRows() {
        when(repository.deleteExpired(any(Instant.class))).thenReturn(7);

        int deleted = service.purgeExpiredSnapshots();

        assertThat(deleted).isEqualTo(7);
        verify(repository).deleteExpired(any(Instant.class));
    }

    private RenderedContentSnapshotService.SnapshotRequest request() {
        return new RenderedContentSnapshotService.SnapshotRequest(
                "tenant-1",
                "workspace-1",
                "campaign-1",
                "job-1",
                "batch-1",
                "message-1",
                "content-1",
                "Rendered",
                "<p>Hello</p>",
                "Hello");
    }
}
