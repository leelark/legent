package com.legent.deliverability.repository;

import com.legent.deliverability.domain.DmarcReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DmarcReportRepository extends JpaRepository<DmarcReport, Long> {
    List<DmarcReport> findByTenantIdAndWorkspaceIdAndDomain(String tenantId, String workspaceId, String domain);
}
