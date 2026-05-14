# Event Flow Map

Last updated: 2026-05-13.

Source: `shared/legent-common/src/main/java/com/legent/common/constant/AppConstants.java`, `docker-compose.yml`, Kafka listener scan.

- Identity: `identity.user.signup`.
- Tenant/config: `system.initialized`, `config.updated`, `tenant.bootstrap.requested`, `tenant.bootstrap.completed`.
- Audience: subscriber/list/segment/import events plus `send.audience.resolution.requested` -> `send.audience.resolved`.
- Campaign: `send.requested`, `send.processing`, `send.batch.created`, `send.completed`, `send.failed`.
- Delivery: `email.send.requested`, `email.sent`, `email.failed`, `email.failed.dlq`, `email.retry.scheduled`, `email.bounced`, `email.complaint`, `email.unsubscribed`.
- Tracking: `email.open`, `email.click`, `email.delivered`, `conversion.event`, `tracking.ingested`, `analytics.aggregated`.
- Automation: `workflow.trigger`, `workflow.started`, `workflow.step.started`, `workflow.step.completed`, `workflow.step.failed`, `workflow.completed`.
- Deliverability/platform: domain/reputation/bounce/complaint/suppression/spam/compliance/search/notification/webhook/integration topics.

Resolved:

- 2026-05-13, source `shared/legent-kafka/.../EventPublisher.java`: high-volume topics no longer use tenant ID as the default key. Publisher derives routing keys from explicit/payload/event metadata and tests cover tenant-key replacement and missing-routing behavior. Low-volume topics still fall back to tenant ID by design.
