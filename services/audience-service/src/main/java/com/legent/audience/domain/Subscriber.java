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
           @UniqueConstraint(name = "uk_subscriber_tenant_workspace_email", columnNames = {"tenant_id", "workspace_id", "email"}),
           @UniqueConstraint(name = "uk_subscriber_tenant_key", columnNames = {"tenant_id", "subscriber_key"})
       })
@Getter
@Setter
@NoArgsConstructor
public class Subscriber extends TenantAwareEntity {

    @Column(name = "subscriber_key", nullable = false, length = 255)
    private String subscriberKey;

    @Column(name = "workspace_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "team_id", length = 36)
    private String teamId;

    @Column(name = "assigned_owner_id", length = 36)
    private String assignedOwnerId;

    @Column(name = "ownership_scope", nullable = false, length = 30)
    private String ownershipScope = "WORKSPACE";

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

    @Column(name = "lead_source", length = 128)
    private String leadSource;

    @Column(name = "acquisition_channel", length = 128)
    private String acquisitionChannel;

    @Column(name = "campaign_source", length = 128)
    private String campaignSource;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "gender", length = 32)
    private String gender;

    @Column(name = "company", length = 255)
    private String company;

    @Column(name = "job_title", length = 255)
    private String jobTitle;

    @Column(name = "industry", length = 255)
    private String industry;

    @Column(name = "department", length = 255)
    private String department;

    @Column(name = "country", length = 128)
    private String country;

    @Column(name = "state", length = 128)
    private String state;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "language", length = 64)
    private String language;

    @Column(name = "profile_image_url", length = 1024)
    private String profileImageUrl;

    @Column(name = "internal_notes", columnDefinition = "text")
    private String internalNotes;

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

    @Column(name = "lifecycle_stage", nullable = false, length = 64)
    private String lifecycleStage = "PROSPECT";

    @Column(name = "lifecycle_stage_at")
    private Instant lifecycleStageAt;

    @Column(name = "open_score", nullable = false)
    private int openScore = 0;

    @Column(name = "click_score", nullable = false)
    private int clickScore = 0;

    @Column(name = "conversion_score", nullable = false)
    private int conversionScore = 0;

    @Column(name = "recency_score", nullable = false)
    private int recencyScore = 0;

    @Column(name = "frequency_score", nullable = false)
    private int frequencyScore = 0;

    @Column(name = "engagement_score", nullable = false)
    private int engagementScore = 0;

    @Column(name = "activity_score", nullable = false)
    private int activityScore = 0;

    @Column(name = "total_score", nullable = false)
    private int totalScore = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "timeline", columnDefinition = "jsonb")
    private java.util.List<Map<String, Object>> timeline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private java.util.List<String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "categories", columnDefinition = "jsonb")
    private java.util.List<String> categories;

    public enum SubscriberStatus {
        ACTIVE, PENDING, SUBSCRIBED, UNSUBSCRIBED, BOUNCED, COMPLAINED, SUPPRESSED, BLOCKED, INACTIVE, HELD, PENDING_CONFIRMATION
    }
}
