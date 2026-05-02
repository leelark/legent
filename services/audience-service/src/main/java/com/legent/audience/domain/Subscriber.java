package com.legent.audience.domain;

import java.util.Map;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


/**
 * Core subscriber profile entity.
 * subscriber_key is the primary business identifier for deduplication.
 */
@Entity
@Table(name = "subscribers", 
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_subscriber_tenant_email", columnNames = {"tenant_id", "email"}),
           @UniqueConstraint(name = "uk_subscriber_tenant_key", columnNames = {"tenant_id", "subscriber_key"})
       })
@Getter
@Setter
@NoArgsConstructor
public class Subscriber extends TenantAwareEntity {

    @Column(name = "subscriber_key", nullable = false, length = 255)
    private String subscriberKey;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SubscriberStatus status = SubscriberStatus.ACTIVE;

    @Column(name = "email_format", nullable = false, length = 10)
    private String emailFormat = "HTML";

    @Column(name = "locale", length = 10)
    private String locale;

    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(name = "source", length = 50)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private Map<String, Object> customFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_preferences", columnDefinition = "jsonb")
    private Map<String, Object> channelPreferences;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "subscribed_at")
    private Instant subscribedAt;

    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;

    @Column(name = "bounced_at")
    private Instant bouncedAt;

    @Column(name = "double_opt_in_confirmed", nullable = false)
    private boolean doubleOptInConfirmed = false;

    @Column(name = "double_opt_in_confirmed_at")
    private Instant doubleOptInConfirmedAt;

    public enum SubscriberStatus {
        ACTIVE, UNSUBSCRIBED, BOUNCED, HELD, BLOCKED, PENDING_CONFIRMATION
    }
}
