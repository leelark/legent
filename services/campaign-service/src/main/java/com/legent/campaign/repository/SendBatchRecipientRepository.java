package com.legent.campaign.repository;

import com.legent.campaign.domain.SendBatchRecipient;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SendBatchRecipientRepository extends JpaRepository<SendBatchRecipient, String> {

    long countByTenantIdAndWorkspaceIdAndJobIdAndBatchIdAndDeletedAtIsNull(
            String tenantId,
            String workspaceId,
            String jobId,
            String batchId);

    long countByTenantIdAndWorkspaceIdAndJobIdAndBatchIdAndStatusInAndDeletedAtIsNull(
            String tenantId,
            String workspaceId,
            String jobId,
            String batchId,
            Collection<SendBatchRecipient.RecipientStatus> statuses);

    List<SendBatchRecipient> findByTenantIdAndWorkspaceIdAndJobIdAndBatchIdAndStatusInAndDeletedAtIsNullOrderBySequenceNumberAsc(
            String tenantId,
            String workspaceId,
            String jobId,
            String batchId,
            Collection<SendBatchRecipient.RecipientStatus> statuses,
            Pageable pageable);
}
