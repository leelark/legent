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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    public enum ListType {
        PUBLICATION, SUPPRESSION, SEND
    }

    public enum ListStatus {
        ACTIVE, ARCHIVED
    }
}
