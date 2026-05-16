package com.legent.content.repository;

import java.util.List;
import java.util.Optional;

import com.legent.content.domain.TemplateApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for template approval operations.
 */
@Repository
public interface TemplateApprovalRepository extends JpaRepository<TemplateApproval, String> {

    List<TemplateApproval> findByTenantIdAndWorkspaceIdAndTemplateIdOrderByRequestedAtDesc(String tenantId, String workspaceId, String templateId);

    @Query("SELECT a FROM TemplateApproval a WHERE a.tenantId = :tid AND a.workspaceId = :workspaceId AND a.templateId = :templateId AND a.status = 'PENDING'")
    Optional<TemplateApproval> findPendingApproval(
            @Param("tid") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("templateId") String templateId);

    Optional<TemplateApproval> findByIdAndTenantIdAndWorkspaceId(String id, String tenantId, String workspaceId);

    List<TemplateApproval> findByTenantIdAndWorkspaceIdAndStatus(String tenantId, String workspaceId, TemplateApproval.ApprovalStatus status);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM TemplateApproval a " +
           "WHERE a.tenantId = :tid AND a.workspaceId = :workspaceId AND a.templateId = :templateId AND a.versionNumber = :version AND a.status = 'PENDING'")
    boolean hasPendingApproval(
            @Param("tid") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("templateId") String templateId,
            @Param("version") int version);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM TemplateApproval a " +
           "WHERE a.tenantId = :tid AND a.workspaceId = :workspaceId AND a.templateId = :templateId AND a.versionNumber = :version AND a.status = 'APPROVED'")
    boolean hasApprovedApproval(
            @Param("tid") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("templateId") String templateId,
            @Param("version") int version);
}
