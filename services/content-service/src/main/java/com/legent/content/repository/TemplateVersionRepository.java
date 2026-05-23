package com.legent.content.repository;

import com.legent.content.domain.TemplateVersion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, String> {

    Optional<TemplateVersion> findFirstByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(String templateId, String tenantId, String workspaceId);

    Optional<TemplateVersion> findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId(String templateId, Integer versionNumber, String tenantId, String workspaceId);

    Integer countByTemplate_IdAndTenantIdAndWorkspaceId(String templateId, String tenantId, String workspaceId);

    Optional<TemplateVersion> findFirstByTemplate_IdAndTenantIdAndWorkspaceIdAndIsPublishedTrueOrderByVersionNumberDesc(String templateId, String tenantId, String workspaceId);

    List<TemplateVersion> findByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
            String templateId,
            String tenantId,
            String workspaceId,
            Pageable pageable);

    @Modifying
    @Query("""
            UPDATE TemplateVersion version
            SET version.isPublished = false
            WHERE version.template.id = :templateId
              AND version.tenantId = :tenantId
              AND version.workspaceId = :workspaceId
              AND version.isPublished = true
              AND version.versionNumber <> :publishedVersion
            """)
    int clearPublishedVersionsExcept(
            @Param("templateId") String templateId,
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("publishedVersion") Integer publishedVersion);
}
