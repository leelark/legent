package com.legent.audience.domain;

import java.util.Map;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


/**
 * List definition entity.
 * Types: PUBLICATION (marketing), SUPPRESSION, SEND (transactional).
 */
@Entity
@Table(name = "subscriber_lists")
@Getter
@Setter
@NoArgsConstructor
public class SubscriberList extends TenantAwareEntity {

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "team_id", length = 36)
    private String teamId;

    @Column(name = "assigned_owner_id", length = 36)
    private String assignedOwnerId;

    @Column(name = "ownership_scope", nullable = false, length = 30)
    private String ownershipScope = "WORKSPACE";

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "list_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ListType listType = ListType.PUBLICATION;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ListStatus status = ListStatus.ACTIVE;

    @Column(name = "member_count", nullable = false)
    private long memberCount = 0;

    @Column(name = "is_dynamic", nullable = false)
    private boolean isDynamic = false;

    @Column(name = "auto_refresh_enabled", nullable = false)
    private boolean autoRefreshEnabled = false;

    @Column(name = "visibility_scope", nullable = false, length = 30)
    private String visibilityScope = "WORKSPACE";

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite = false;

    @Column(name = "folder", length = 255)
    private String folder;

    @Column(name = "category", length = 128)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private java.util.List<String> tags = new java.util.ArrayList<>();

    public enum ListType {
        PUBLICATION, SUPPRESSION, SEND
    }

    public enum ListStatus {
        ACTIVE, ARCHIVED
    }
}
