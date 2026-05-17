package com.legent.content.repository;

import com.legent.content.domain.RenderedContentSnapshot;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RenderedContentSnapshotRepository extends JpaRepository<RenderedContentSnapshot, String> {

    Optional<RenderedContentSnapshot> findByTenantIdAndWorkspaceIdAndReferenceIdAndDeletedAtIsNull(
            String tenantId,
            String workspaceId,
            String referenceId);

    @Modifying
    @Query("DELETE FROM RenderedContentSnapshot snapshot WHERE snapshot.expiresAt < :now")
    int deleteExpired(Instant now);
}
