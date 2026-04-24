package com.legent.delivery.domain;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "smtp_providers")
@Getter
@Setter
public class SmtpProvider extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // SMTP, AWS_SES, POSTAL

    private String host;
    private Integer port;
    private String username;
    
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "encrypted_password")
    private String encryptedPassword;

    @Column(name = "encryption_iv")
    private String encryptionIv;

    @Column(name = "is_active")
    private boolean isActive = true;

    private Integer priority = 1;

    @Column(name = "max_send_rate")
    private Integer maxSendRate; // per second
}
