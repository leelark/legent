package com.legent.content.repository;

import com.legent.content.domain.DynamicContentRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DynamicContentRuleRepository extends JpaRepository<DynamicContentRule, String> {
    List<DynamicContentRule> findByTenantIdAndWorkspaceIdAndTemplateIdAndDeletedAtIsNullOrderByPriorityAsc(String tenantId, String workspaceId, String templateId);
    List<DynamicContentRule> findByTenantIdAndWorkspaceIdAndTemplateIdAndSlotKeyAndActiveTrueAndDeletedAtIsNullOrderByPriorityAsc(String tenantId, String workspaceId, String templateId, String slotKey);
    Optional<DynamicContentRule> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);
}
