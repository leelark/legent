package com.legent.audience.service;

import com.legent.audience.domain.AudienceResolutionChunk;
import com.legent.audience.repository.AudienceResolutionChunkRepository;
import com.legent.common.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudienceResolutionChunkServiceTest {

    @Mock
    private AudienceResolutionChunkRepository repository;

    @Test
    void storeChunkPersistsTenantScopedPayloadAndReturnsInternalReference() {
        AudienceResolutionChunkService service = new AudienceResolutionChunkService(repository);
        when(repository.save(any(AudienceResolutionChunk.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AudienceResolutionChunkService.ChunkReference reference = service.storeChunk(
                "tenant-1",
                "workspace-1",
                "campaign-1",
                "job-1",
                "job-1:audience:0",
                0,
                2,
                3,
                false,
                List.of(Map.of("email", "one@example.com", "subscriberId", "sub-1")));

        ArgumentCaptor<AudienceResolutionChunk> chunkCaptor = ArgumentCaptor.forClass(AudienceResolutionChunk.class);
        verify(repository).save(chunkCaptor.capture());
        AudienceResolutionChunk saved = chunkCaptor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(saved.getChunkSize()).isEqualTo(1);
        assertThat(saved.getSubscriberPayload()).containsExactly(Map.of("email", "one@example.com", "subscriberId", "sub-1"));
        assertThat(reference.chunkId()).isEqualTo("job-1:audience:0");
        assertThat(reference.storageBackend()).isEqualTo(AudienceResolutionChunkService.STORAGE_BACKEND);
        assertThat(reference.referenceType()).isEqualTo(AudienceResolutionChunkService.REFERENCE_TYPE);
        assertThat(reference.chunkUri()).isEqualTo("/api/v1/audience-resolution-chunks/job-1:audience:0/internal");
    }

    @Test
    void getChunkFailsClosedOnTenantWorkspaceOrJobMismatch() {
        AudienceResolutionChunkService service = new AudienceResolutionChunkService(repository);
        when(repository.findByTenantIdAndWorkspaceIdAndJobIdAndChunkIdAndDeletedAtIsNull(
                "tenant-1",
                "workspace-1",
                "job-1",
                "chunk-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getChunk("tenant-1", "workspace-1", "job-1", "chunk-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void storeChunkRejectsMissingScopeBeforeRepositoryAccess() {
        AudienceResolutionChunkService service = new AudienceResolutionChunkService(repository);

        assertThatThrownBy(() -> service.storeChunk(
                "tenant-1",
                " ",
                "campaign-1",
                "job-1",
                "chunk-1",
                0,
                1,
                1,
                true,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId");
        verify(repository, never()).save(any());
    }
}
