package com.legent.platform.domain;

import java.time.Instant;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "search_index_docs")
@Getter
@Setter
public class SearchIndexDoc {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "entity_type", nullable = false)
    private String entityType; // SUBSCRIBER, CAMPAIGN, WORKFLOW

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    private String title;

    @Column(name = "searchable_text", columnDefinition = "TEXT")
    private String searchableText; // Flatted string for ILIKE searches

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
