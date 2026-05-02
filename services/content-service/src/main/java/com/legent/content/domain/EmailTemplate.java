package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "email_templates",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_template_tenant_name", columnNames = {"tenant_id", "name"})
       })
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

    // Draft mode fields
    @Column(name = "draft_subject", length = 500)
    private String draftSubject;

    @Column(name = "draft_html_content", columnDefinition = "TEXT")
    private String draftHtmlContent;

    @Column(name = "draft_text_content", columnDefinition = "TEXT")
    private String draftTextContent;

    // Approval workflow fields
    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired = false;

    @Column(name = "current_approver", length = 36)
    private String currentApprover;

    @Column(name = "last_published_version")
    private Integer lastPublishedVersion;

    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    public enum TemplateStatus {
        DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, ARCHIVED
    }

    public enum TemplateType {
        REGULAR, AUTOMATION, TRANSACTIONAL
    }
}
