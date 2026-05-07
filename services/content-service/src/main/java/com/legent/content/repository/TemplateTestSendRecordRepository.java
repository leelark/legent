package com.legent.content.repository;

import com.legent.content.domain.TemplateTestSendRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateTestSendRecordRepository extends JpaRepository<TemplateTestSendRecord, String> {
    List<TemplateTestSendRecord> findByTenantIdAndTemplateIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, String templateId);
}
