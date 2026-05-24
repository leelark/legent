package com.legent.audience.repository;

import java.util.List;

import com.legent.audience.domain.ContactLifecycleAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactLifecycleAuditRepository extends JpaRepository<ContactLifecycleAudit, String> {

    List<ContactLifecycleAudit> findTop50ByTenantIdAndWorkspaceIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
            String tenantId,
            String workspaceId,
            String subjectType,
            String subjectId);
}
