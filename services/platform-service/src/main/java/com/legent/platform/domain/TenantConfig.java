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
@Table(name = "tenant_configs")
@Getter
@Setter
public class TenantConfig {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "theme_color")
    private String themeColor = "#4F46E5";

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "timezone")
    private String timezone = "UTC";

    @Type(JsonBinaryType.class)
    @Column(name = "features_json", columnDefinition = "jsonb")
    private String featuresJson;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
