package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "provider_decision_traces")
@Getter
@Setter
public class ProviderDecisionTrace extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "provider_id", nullable = false, length = 64)
    private String providerId;

    @Column(name = "sender_domain", length = 255)
    private String senderDomain;

    @Column(name = "recipient_domain", length = 255)
    private String recipientDomain;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "selected", nullable = false)
    private Boolean selected;

    @Column(name = "factors", columnDefinition = "TEXT")
    private String factors;
}
