package com.legent.campaign.repository;

import java.util.List;

import com.legent.campaign.domain.SendBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SendBatchRepository extends JpaRepository<SendBatch, String> {
    List<SendBatch> findByTenantIdAndJobIdAndDeletedAtIsNull(String tenantId, String jobId);

    List<SendBatch> findByTenantIdAndStatusInAndDeletedAtIsNull(String tenantId, List<SendBatch.BatchStatus> statuses);

    @Modifying
    @Query("UPDATE SendBatch b SET b.status = :status WHERE b.jobId = :jobId AND b.tenantId = :tenantId")
    void updateStatusByJobId(@Param("tenantId") String tenantId, @Param("jobId") String jobId, @Param("status") SendBatch.BatchStatus status);
}
