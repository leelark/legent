package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "emails")
@Data
@EqualsAndHashCode(callSuper = true)
public class Email extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "template_id")
    private String templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailStatus status = EmailStatus.DRAFT;

    public enum EmailStatus {
        DRAFT, SENT, SCHEDULED, ARCHIVED
    }
}
