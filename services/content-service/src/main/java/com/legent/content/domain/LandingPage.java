package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "landing_pages",
        uniqueConstraints = @UniqueConstraint(name = "uq_landing_page_slug", columnNames = {"slug"}))
@Getter
@Setter
public class LandingPage extends TenantAwareEntity {
    @Column(name = "workspace_id", length = 36)
    private String workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.DRAFT;

    @Column(name = "html_content", columnDefinition = "TEXT")
    private String htmlContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String metadata = "{}";

    @Column(name = "published_at")
    private Instant publishedAt;

    public enum Status {
        DRAFT, PUBLISHED, ARCHIVED
    }
}
