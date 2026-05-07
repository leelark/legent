package com.legent.identity.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@SQLDelete(sql = "UPDATE user_preferences SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class UserPreference extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "ui_mode", nullable = false)
    private String uiMode;

    @Column(nullable = false)
    private String theme;

    @Column(nullable = false)
    private String density;

    @Column(name = "sidebar_collapsed", nullable = false)
    private boolean sidebarCollapsed;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;
}
