package com.legent.common.constant;

/**
 * Global application constants shared across all services.
 */
public final class AppConstants {

    private AppConstants() {
        // Utility class
    }

    // ── API ──
    public static final String API_BASE_PATH = "/api/v1";
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // ── Headers ──
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    // ── Cache ──
    public static final String CACHE_CONFIG_PREFIX = "config:";
    public static final String CACHE_FEATURE_FLAG_PREFIX = "ff:";
    public static final String CACHE_TENANT_PREFIX = "tenant:";
    public static final long CACHE_CONFIG_TTL_SECONDS = 300;     // 5 minutes
    public static final long CACHE_FLAG_TTL_SECONDS = 60;        // 1 minute
    public static final long CACHE_TENANT_TTL_SECONDS = 600;     // 10 minutes

    // ── Kafka Topics ──
    public static final String TOPIC_SYSTEM_INITIALIZED = "system.initialized";
    public static final String TOPIC_CONFIG_UPDATED = "config.updated";

    // Audience Topics
    public static final String TOPIC_SUBSCRIBER_CREATED = "subscriber.created";
    public static final String TOPIC_SUBSCRIBER_UPDATED = "subscriber.updated";
    public static final String TOPIC_SUBSCRIBER_DELETED = "subscriber.deleted";
    public static final String TOPIC_SEGMENT_CREATED = "segment.created";
    public static final String TOPIC_SEGMENT_UPDATED = "segment.updated";
    public static final String TOPIC_SEGMENT_RECOMPUTED = "segment.recomputed";
    public static final String TOPIC_IMPORT_STARTED = "import.started";
    public static final String TOPIC_IMPORT_COMPLETED = "import.completed";
    public static final String TOPIC_IMPORT_FAILED = "import.failed";

    // Campaign Topics
    public static final String TOPIC_SEND_REQUESTED = "send.requested";
    public static final String TOPIC_AUDIENCE_RESOLUTION_REQUESTED = "send.audience.resolution.requested";
    public static final String TOPIC_AUDIENCE_RESOLVED = "send.audience.resolved";
    public static final String TOPIC_SEND_PROCESSING = "send.processing";
    public static final String TOPIC_BATCH_CREATED = "send.batch.created";
    public static final String TOPIC_BATCH_COMPLETED = "send.batch.completed";
    public static final String TOPIC_SEND_COMPLETED = "send.completed";
    public static final String TOPIC_SEND_FAILED = "send.failed";
    public static final String TOPIC_CONTENT_PUBLISHED = "content.published";

    // Delivery Topics
    public static final String TOPIC_EMAIL_SEND_REQUESTED = "email.send.requested";
    public static final String TOPIC_EMAIL_SENT = "email.sent";
    public static final String TOPIC_EMAIL_FAILED = "email.failed";
    public static final String TOPIC_EMAIL_FAILED_DLQ = "email.failed.dlq";
    public static final String TOPIC_EMAIL_RETRY_SCHEDULED = "email.retry.scheduled";
    public static final String TOPIC_EMAIL_BOUNCED = "email.bounced";
    public static final String TOPIC_EMAIL_COMPLAINT = "email.complaint";

    // Tracking Topics
    public static final String TOPIC_EMAIL_OPEN = "email.open";
    public static final String TOPIC_EMAIL_CLICK = "email.click";
    public static final String TOPIC_EMAIL_DELIVERED = "email.delivered";
    public static final String TOPIC_CONVERSION_EVENT = "conversion.event";
    public static final String TOPIC_TRACKING_INGESTED = "tracking.ingested";
    public static final String TOPIC_ANALYTICS_AGGREGATED = "analytics.aggregated";

    // Automation Topics
    public static final String TOPIC_WORKFLOW_TRIGGER = "workflow.trigger";
    public static final String TOPIC_WORKFLOW_STARTED = "workflow.started";
    public static final String TOPIC_WORKFLOW_STEP_STARTED = "workflow.step.started";
    public static final String TOPIC_WORKFLOW_STEP_COMPLETED = "workflow.step.completed";
    public static final String TOPIC_WORKFLOW_STEP_FAILED = "workflow.step.failed";
    public static final String TOPIC_WORKFLOW_COMPLETED = "workflow.completed";

    // Deliverability Topics
    public static final String TOPIC_DOMAIN_VERIFIED = "domain.verified";
    public static final String TOPIC_REPUTATION_UPDATED = "reputation.updated";
    public static final String TOPIC_BOUNCE_CLASSIFIED = "bounce.classified";
    public static final String TOPIC_COMPLAINT_RECEIVED = "complaint.received";
    public static final String TOPIC_SUPPRESSION_UPDATED = "suppression.updated";
    public static final String TOPIC_SPAM_SCORE_GENERATED = "spam.score.generated";
    public static final String TOPIC_COMPLIANCE_VIOLATION = "compliance.violation";

    // Platform & Integration Topics
    public static final String TOPIC_SEARCH_INDEX_UPDATED = "search.index.updated";
    public static final String TOPIC_NOTIFICATION_CREATED = "notification.created";
    public static final String TOPIC_WEBHOOK_TRIGGERED = "webhook.triggered";
    public static final String TOPIC_INTEGRATION_SYNC = "integration.sync";

    // ── Kafka Consumer Groups ──
    public static final String GROUP_FOUNDATION = "foundation-service";
    public static final String GROUP_AUDIENCE = "audience-service";
    public static final String GROUP_CAMPAIGN = "campaign-service";
    public static final String GROUP_DELIVERY = "delivery-service";
    public static final String GROUP_DELIVERY_FAILED = "delivery-service-failed";
    public static final String GROUP_TRACKING = "tracking-service";
    public static final String GROUP_AUTOMATION = "automation-service";
    public static final String GROUP_DELIVERABILITY = "deliverability-service";
    public static final String GROUP_PLATFORM = "platform-service";

    // ── Cache (Audience & Campaign) ──
    public static final String CACHE_SUBSCRIBER_PREFIX = "subscriber:";
    public static final String CACHE_SEGMENT_PREFIX = "segment:";
    public static final String CACHE_SEGMENT_COUNT_PREFIX = "segment-count:";
    public static final String CACHE_THROTTLE_DOMAIN_PREFIX = "throttle:domain:";
    public static final String CACHE_THROTTLE_ISP_PREFIX = "throttle:isp:";
    public static final long CACHE_SUBSCRIBER_TTL_SECONDS = 300;   // 5 minutes
    public static final long CACHE_SEGMENT_COUNT_TTL_SECONDS = 120; // 2 minutes

    // ── Import & Send Batching ──
    public static final int IMPORT_CHUNK_SIZE = 5000;
    public static final int SEND_BATCH_SIZE = 1000;

    public static final int IMPORT_MAX_ERRORS = 1000;

    // ── Validation ──
    public static final int MAX_NAME_LENGTH = 255;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_KEY_LENGTH = 128;
    public static final int MAX_EMAIL_LENGTH = 320;
}
