package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "template_versions")
public class TemplateVersion extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private EmailTemplate template;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String htmlContent;

    @Column(columnDefinition = "TEXT")
    private String textContent;

    @Column(columnDefinition = "TEXT")
    private String changes;

    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = false;
}
