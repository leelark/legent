package com.legent.content.repository;

import com.legent.content.domain.DynamicContentRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DynamicContentRuleRepository extends JpaRepository<DynamicContentRule, String> {
    List<DynamicContentRule> findByTenantIdAndTemplateIdAndDeletedAtIsNullOrderByPriorityAsc(String tenantId, String templateId);
    List<DynamicContentRule> findByTenantIdAndTemplateIdAndSlotKeyAndActiveTrueAndDeletedAtIsNullOrderByPriorityAsc(String tenantId, String templateId, String slotKey);
    Optional<DynamicContentRule> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);
}
