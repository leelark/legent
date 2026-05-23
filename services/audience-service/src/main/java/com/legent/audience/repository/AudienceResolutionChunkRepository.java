package com.legent.audience.repository;

import com.legent.audience.domain.AudienceResolutionChunk;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudienceResolutionChunkRepository extends JpaRepository<AudienceResolutionChunk, String> {

    Optional<AudienceResolutionChunk> findByTenantIdAndWorkspaceIdAndJobIdAndChunkIdAndDeletedAtIsNull(
            String tenantId,
            String workspaceId,
            String jobId,
            String chunkId);
}
