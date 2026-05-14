package com.legent.audience.repository;

import com.legent.audience.domain.DataExtensionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataExtensionRecordRepository extends JpaRepository<DataExtensionRecord, String> {

    Page<DataExtensionRecord> findByTenantIdAndWorkspaceIdAndDataExtensionId(String tenantId,
                                                                             String workspaceId,
                                                                             String dataExtensionId,
                                                                             Pageable pageable);

    @Query("SELECT COUNT(r) FROM DataExtensionRecord r WHERE r.tenantId = :tenantId AND r.workspaceId = :workspaceId AND r.dataExtensionId = :deId")
    long countByTenantWorkspaceAndDataExtension(@Param("tenantId") String tenantId,
                                                @Param("workspaceId") String workspaceId,
                                                @Param("deId") String dataExtensionId);
}
