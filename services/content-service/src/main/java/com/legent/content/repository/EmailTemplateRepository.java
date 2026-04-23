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

    Page<EmailTemplate> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);

    List<EmailTemplate> findByTenantIdAndDeletedAtIsNull(String tenantId);

    Optional<EmailTemplate> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);

    boolean existsByTenantIdAndNameAndDeletedAtIsNull(String tenantId, String name);

    @Query("SELECT t FROM EmailTemplate t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL AND t.templateType = :templateType")
    Page<EmailTemplate> findByTenantIdAndTemplateTypeAndDeletedAtIsNull(@Param("tenantId") String tenantId, @Param("templateType") EmailTemplate.TemplateType templateType, Pageable pageable);

    @Query("SELECT t FROM EmailTemplate t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL AND LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<EmailTemplate> searchByName(@Param("tenantId") String tenantId, @Param("query") String query);
}
