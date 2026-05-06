package com.legent.delivery.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "suppression_signals")
@Getter
@Setter
public class SuppressionSignal extends BaseEntity {

    public enum SignalType {
        HARD_BOUNCE, SOFT_BOUNCE, COMPLAINT
    }

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId = "workspace-default";

    @Column(name = "team_id", length = 64)
    private String teamId;

    @Column(name = "ownership_scope", nullable = false, length = 32)
    private String ownershipScope = "WORKSPACE";

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "source_message_id")
    private String sourceMessageId;
}
