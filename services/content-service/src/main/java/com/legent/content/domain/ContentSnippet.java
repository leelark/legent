package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "content_snippets",
        uniqueConstraints = @UniqueConstraint(name = "uq_content_snippet_workspace_key", columnNames = {"tenant_id", "workspace_id", "snippet_key"}))
@Getter
@Setter
public class ContentSnippet extends TenantAwareEntity {
    @Column(name = "workspace_id", length = 36)
    private String workspaceId;

    @Column(name = "snippet_key", nullable = false, length = 128)
    private String snippetKey;

    @Column(nullable = false)
    private String name;

    @Column(name = "snippet_type", nullable = false, length = 32)
    private String snippetType = "HTML";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_global", nullable = false)
    private Boolean isGlobal = false;
}
