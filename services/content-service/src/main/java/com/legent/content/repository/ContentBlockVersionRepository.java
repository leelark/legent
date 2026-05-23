package com.legent.content.repository;

import com.legent.content.domain.ContentBlockVersion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContentBlockVersionRepository extends JpaRepository<ContentBlockVersion, String> {
    Optional<ContentBlockVersion> findFirstByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(String blockId, String tenantId, String workspaceId);
    Optional<ContentBlockVersion> findByBlock_IdAndVersionNumberAndTenantIdAndWorkspaceId(String blockId, Integer versionNumber, String tenantId, String workspaceId);
    Optional<ContentBlockVersion> findFirstByBlock_IdAndTenantIdAndWorkspaceIdAndIsPublishedTrueOrderByVersionNumberDesc(String blockId, String tenantId, String workspaceId);
    List<ContentBlockVersion> findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(String blockId, String tenantId, String workspaceId);
    List<ContentBlockVersion> findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(String blockId, String tenantId, String workspaceId, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE ContentBlockVersion version
            SET version.isPublished = false
            WHERE version.block.id = :blockId
              AND version.tenantId = :tenantId
              AND version.workspaceId = :workspaceId
              AND version.isPublished = true
              AND version.versionNumber <> :publishedVersion
            """)
    int clearPublishedVersionsExcept(
            @Param("blockId") String blockId,
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("publishedVersion") Integer publishedVersion);
}
