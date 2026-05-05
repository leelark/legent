package com.legent.content.repository;

import com.legent.content.domain.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, String> {

    Optional<TemplateVersion> findFirstByTemplate_IdAndTenantIdOrderByVersionNumberDesc(String templateId, String tenantId);

    Optional<TemplateVersion> findByTemplate_IdAndVersionNumberAndTenantId(String templateId, Integer versionNumber, String tenantId);

    Integer countByTemplate_IdAndTenantId(String templateId, String tenantId);

    Optional<TemplateVersion> findFirstByTemplate_IdAndTenantIdAndIsPublishedTrueOrderByVersionNumberDesc(String templateId, String tenantId);

    List<TemplateVersion> findByTemplate_IdAndTenantIdOrderByVersionNumberDesc(String templateId, String tenantId);
}
