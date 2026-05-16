package com.legent.content.repository;

import com.legent.content.domain.RenderValidationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RenderValidationReportRepository extends JpaRepository<RenderValidationReport, String> {
    List<RenderValidationReport> findByTenantIdAndWorkspaceIdAndTemplateIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, String workspaceId, String templateId);
}
