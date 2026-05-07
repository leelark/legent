package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "personalization_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uq_personalization_token", columnNames = {"tenant_id", "token_key"}))
@Getter
@Setter
public class PersonalizationToken extends TenantAwareEntity {
    @Column(name = "token_key", nullable = false, length = 128)
    private String tokenKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType = "SUBSCRIBER";

    @Column(name = "data_path")
    private String dataPath;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "sample_value", columnDefinition = "TEXT")
    private String sampleValue;

    @Column(nullable = false)
    private Boolean required = false;
}
