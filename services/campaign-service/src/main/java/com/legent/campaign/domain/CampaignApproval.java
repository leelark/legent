package com.legent.campaign.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Campaign approval request for workflow management.
 */
@Entity
@Table(name = "campaign_approvals")
@Getter
@Setter
@NoArgsConstructor
public class CampaignApproval extends TenantAwareEntity {

    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    @Column(name = "requested_by", nullable = false, length = 36)
    private String requestedBy;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "approved_by", length = 36)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }
}
