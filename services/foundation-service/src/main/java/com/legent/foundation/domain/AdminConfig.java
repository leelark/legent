package com.legent.foundation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admin_configs")
@Getter
@Setter
public class AdminConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "config_key")
    @JsonProperty("key")
    private String configKey;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(length = 50)
    private String configType = "STRING";

    @Column(name = "is_editable")
    private Boolean editable = true;

    @Column(name = "tenant_id", length = 26)
    private String tenantId;
}
