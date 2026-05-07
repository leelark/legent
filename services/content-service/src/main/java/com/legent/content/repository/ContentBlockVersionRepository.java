package com.legent.content.repository;

import com.legent.content.domain.ContentBlockVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentBlockVersionRepository extends JpaRepository<ContentBlockVersion, String> {
    Optional<ContentBlockVersion> findFirstByBlock_IdAndTenantIdOrderByVersionNumberDesc(String blockId, String tenantId);
    Optional<ContentBlockVersion> findByBlock_IdAndVersionNumberAndTenantId(String blockId, Integer versionNumber, String tenantId);
    Optional<ContentBlockVersion> findFirstByBlock_IdAndTenantIdAndIsPublishedTrueOrderByVersionNumberDesc(String blockId, String tenantId);
    List<ContentBlockVersion> findByBlock_IdAndTenantIdOrderByVersionNumberDesc(String blockId, String tenantId);
}
