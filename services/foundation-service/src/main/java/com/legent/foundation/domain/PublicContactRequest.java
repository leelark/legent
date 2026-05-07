package com.legent.foundation.domain;

import com.legent.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "public_contact_requests")
@Getter
@Setter
@SQLDelete(sql = "UPDATE public_contact_requests SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class PublicContactRequest extends BaseEntity {

    @Column(length = 120)
    private String name;

    @Column(name = "work_email", nullable = false, length = 255)
    private String workEmail;

    @Column(nullable = false, length = 160)
    private String company;

    @Column(length = 120)
    private String interest;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "source_page", length = 120)
    private String sourcePage;

    @Column(nullable = false, length = 32)
    private String status = "RECEIVED";
}
