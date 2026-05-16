package com.legent.content.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "brand_kits",
        uniqueConstraints = @UniqueConstraint(name = "uq_brand_kit_workspace_name", columnNames = {"tenant_id", "workspace_id", "name"}))
@Getter
@Setter
public class BrandKit extends TenantAwareEntity {
    @Column(name = "workspace_id", length = 36)
    private String workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "primary_color", length = 32)
    private String primaryColor;

    @Column(name = "secondary_color", length = 32)
    private String secondaryColor;

    @Column(name = "font_family")
    private String fontFamily;

    @Column(name = "footer_html", columnDefinition = "TEXT")
    private String footerHtml;

    @Column(name = "legal_text", columnDefinition = "TEXT")
    private String legalText;

    @Column(name = "default_from_name")
    private String defaultFromName;

    @Column(name = "default_from_email")
    private String defaultFromEmail;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
}
