package com.legent.campaign.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "campaign_locks")
@Getter
@Setter
public class CampaignLock extends TenantAwareEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false, length = 26)
    private String campaignId;

    @Column(name = "lock_hash", nullable = false, length = 128)
    private String lockHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String snapshot = "{}";

    @Column(nullable = false, length = 32)
    private String status = STATUS_ACTIVE;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt = Instant.now();

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    @Column(name = "superseded_at")
    private Instant supersededAt;
}
