package com.legent.platform.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "webhooks")
@Getter
@Setter
public class WebhookConfig extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "endpoint_url", columnDefinition = "TEXT", nullable = false)
    private String endpointUrl;

    @Column(name = "secret_key")
    private String secretKey;

    @Type(JsonBinaryType.class)
    @Column(name = "events_subscribed", columnDefinition = "jsonb")
    private String eventsSubscribed; // JSON Array of strings e.g., ["email.bounced", "workflow.completed"]

    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "created_by")
    private String createdBy;
}
