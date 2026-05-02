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

    @Column(name = "encrypted_password")
    private String encryptedPassword;

    @Column(name = "encryption_iv")
    private String encryptionIv;

    @Column(name = "encryption_salt")
    private String encryptionSalt;

    @Column(name = "is_active")
    private boolean isActive = true;

    private Integer priority = 1;

    @Column(name = "max_send_rate")
    private Integer maxSendRate; // per second

    // Health monitoring fields
    @Column(name = "health_check_enabled")
    private boolean healthCheckEnabled = true;

    @Column(name = "health_check_url", length = 500)
    private String healthCheckUrl;

    @Column(name = "health_check_interval_seconds")
    private Integer healthCheckIntervalSeconds = 60;

    @Column(name = "last_health_check_at")
    private java.time.Instant lastHealthCheckAt;

    @Column(name = "health_status", length = 20)
    private String healthStatus = "UNKNOWN";
}
