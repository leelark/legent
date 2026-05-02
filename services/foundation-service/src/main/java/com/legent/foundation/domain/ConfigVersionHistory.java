package com.legent.foundation.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Configuration version history for tracking all config changes.
 */
@Entity
@Table(name = "config_version_history")
@Getter
@Setter
@NoArgsConstructor
public class ConfigVersionHistory {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType = "STRING";

    @Column(name = "category", nullable = false, length = 64)
    private String category = "GENERAL";

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_encrypted", nullable = false)
    private boolean isEncrypted = false;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;

    @Column(name = "changed_by", length = 36)
    private String changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "rollback_to_version")
    private Integer rollbackToVersion;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
    }

    public enum ChangeType {
        CREATE, UPDATE, DELETE, ROLLBACK
    }
}
