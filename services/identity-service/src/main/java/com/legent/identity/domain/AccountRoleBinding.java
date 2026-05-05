package com.legent.identity.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "account_role_bindings")
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE account_role_bindings SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class AccountRoleBinding extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "membership_id", nullable = false)
    private String membershipId;

    @Column(name = "role_key", nullable = false)
    private String roleKey;

    @Column(name = "scope_type", nullable = false)
    private String scopeType = "TENANT";

    @Column(name = "scope_id")
    private String scopeId;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
