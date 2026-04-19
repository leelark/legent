package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "content_blocks")
public class ContentBlock extends TenantAwareEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String blockType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "JSONB")
    private String styles;

    @Column(columnDefinition = "JSONB")
    private String settings;

    @Column(nullable = false)
    private Boolean isGlobal = false;
}