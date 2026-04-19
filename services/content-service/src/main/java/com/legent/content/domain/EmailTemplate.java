package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "email_templates")
public class EmailTemplate extends TenantAwareEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String htmlContent;

    @Column(columnDefinition = "TEXT")
    private String textContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateStatus status = TemplateStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateType templateType = TemplateType.REGULAR;

    @Column(length = 50)
    private String category;

    @ElementCollection
    @CollectionTable(name = "template_tags", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "tag")
    private List<String> tags;

    @Column(columnDefinition = "JSONB")
    private String metadata;

    public enum TemplateStatus {
        DRAFT, PUBLISHED, ARCHIVED
    }

    public enum TemplateType {
        REGULAR, AUTOMATION, TRANSACTIONAL
    }
}