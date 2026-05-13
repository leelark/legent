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

Open risk:

- `shared/legent-kafka/.../EventPublisher.java` defaults event key to tenant ID. High-volume topics need job/batch/provider/domain/shard-aware keys.
