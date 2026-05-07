package com.legent.content.repository;

import com.legent.content.domain.LandingPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LandingPageRepository extends JpaRepository<LandingPage, String> {
    Page<LandingPage> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);
    Optional<LandingPage> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);
    Optional<LandingPage> findFirstBySlugAndStatusAndDeletedAtIsNull(String slug, LandingPage.Status status);
    boolean existsByTenantIdAndSlugAndDeletedAtIsNull(String tenantId, String slug);
    boolean existsBySlugAndDeletedAtIsNull(String slug);
}
