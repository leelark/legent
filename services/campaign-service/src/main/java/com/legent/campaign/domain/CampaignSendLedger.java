package com.legent.campaign.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "campaign_send_ledger")
@Getter
@Setter
public class CampaignSendLedger extends TenantAwareEntity {

    public enum SendState {
        RESERVED, SENT, FAILED, SUPPRESSED, HOLDOUT
    }

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "message_id", nullable = false, length = 256)
    private String messageId;

    @Column(name = "subscriber_id", length = 64)
    private String subscriberId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "experiment_id", length = 26)
    private String experimentId;

    @Column(name = "variant_id", length = 26)
    private String variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_state", nullable = false, length = 32)
    private SendState sendState = SendState.RESERVED;

    @Column(length = 128)
    private String reason;

    @Column(name = "cost_reserved", nullable = false, precision = 18, scale = 6)
    private BigDecimal costReserved = BigDecimal.ZERO;

    @Column(name = "cost_actual", nullable = false, precision = 18, scale = 6)
    private BigDecimal costActual = BigDecimal.ZERO;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;
}
