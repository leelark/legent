package com.legent.foundation.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(name = "public_contents")
@Getter
@Setter
@SQLDelete(sql = "UPDATE public_contents SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class PublicContent extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "workspace_id")
    private String workspaceId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "page_key", nullable = false)
    private String pageKey;

    @Column
    private String slug;

    @Column
    private String title;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "seo_meta", columnDefinition = "jsonb", nullable = false)
    private String seoMeta;

    @Column(name = "published_at")
    private Instant publishedAt;
}

