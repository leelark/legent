package com.legent.content.repository;

import com.legent.content.domain.ContentBlockVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentBlockVersionRepository extends JpaRepository<ContentBlockVersion, String> {
    Optional<ContentBlockVersion> findFirstByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(String blockId, String tenantId, String workspaceId);
    Optional<ContentBlockVersion> findByBlock_IdAndVersionNumberAndTenantIdAndWorkspaceId(String blockId, Integer versionNumber, String tenantId, String workspaceId);
    Optional<ContentBlockVersion> findFirstByBlock_IdAndTenantIdAndWorkspaceIdAndIsPublishedTrueOrderByVersionNumberDesc(String blockId, String tenantId, String workspaceId);
    List<ContentBlockVersion> findByBlock_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(String blockId, String tenantId, String workspaceId);
}
