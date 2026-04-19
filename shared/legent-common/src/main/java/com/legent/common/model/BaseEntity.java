package com.legent.common.model;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Abstract base entity providing:
 * - ULID-based primary key generation
 * - Audit fields (createdAt, updatedAt, createdBy)
 * - Soft delete support via deletedAt
 * - Optimistic locking via version
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(name = "id", length = 26, nullable = false, updatable = false)
    private String id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 26)
    private String createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Marks the entity as soft-deleted.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /**
     * Returns true if the entity has been soft-deleted.
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
