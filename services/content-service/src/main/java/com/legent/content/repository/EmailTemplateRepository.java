package com.legent.content.repository;

import com.legent.content.domain.EmailTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, String> {

    Page<EmailTemplate> findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(String tenantId, String workspaceId, Pageable pageable);

    List<EmailTemplate> findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(String tenantId, String workspaceId);

    Optional<EmailTemplate> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);

    boolean existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(String tenantId, String workspaceId, String name);

    @Query("SELECT t FROM EmailTemplate t WHERE t.tenantId = :tenantId AND t.workspaceId = :workspaceId AND t.deletedAt IS NULL AND t.templateType = :templateType")
    Page<EmailTemplate> findByTenantIdAndWorkspaceIdAndTemplateTypeAndDeletedAtIsNull(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("templateType") EmailTemplate.TemplateType templateType,
            Pageable pageable);

    @Query("SELECT t FROM EmailTemplate t WHERE t.tenantId = :tenantId AND t.workspaceId = :workspaceId AND t.deletedAt IS NULL AND LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<EmailTemplate> searchByName(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("query") String query);
}
