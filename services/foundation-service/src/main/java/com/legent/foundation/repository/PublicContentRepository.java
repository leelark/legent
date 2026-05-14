package com.legent.foundation.repository;

import com.legent.foundation.domain.PublicContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublicContentRepository extends JpaRepository<PublicContent, String> {
    Optional<PublicContent> findByIdAndTenantIdAndWorkspaceId(String id, String tenantId, String workspaceId);

    Optional<PublicContent> findByTenantIdAndWorkspaceIdAndContentTypeAndPageKeyAndSlug(
            String tenantId,
            String workspaceId,
            String contentType,
            String pageKey,
            String slug);

    Optional<PublicContent> findByTenantIdAndWorkspaceIdAndContentTypeAndPageKeyAndStatus(
            String tenantId,
            String workspaceId,
            String contentType,
            String pageKey,
            String status);

    Optional<PublicContent> findByTenantIdAndWorkspaceIdAndContentTypeAndSlugAndStatus(
            String tenantId,
            String workspaceId,
            String contentType,
            String slug,
            String status);

    List<PublicContent> findByTenantIdAndWorkspaceIdAndContentTypeAndStatusOrderByPublishedAtDesc(
            String tenantId,
            String workspaceId,
            String contentType,
            String status);

    List<PublicContent> findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(String tenantId, String workspaceId);
}
