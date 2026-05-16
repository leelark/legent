package com.legent.content.repository;

import com.legent.content.domain.LandingPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LandingPageRepository extends JpaRepository<LandingPage, String> {
    Page<LandingPage> findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(String tenantId, String workspaceId, Pageable pageable);
    Optional<LandingPage> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);
    Optional<LandingPage> findFirstBySlugAndStatusAndDeletedAtIsNull(String slug, LandingPage.Status status);
    boolean existsBySlugAndDeletedAtIsNull(String slug);
}
