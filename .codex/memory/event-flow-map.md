# Event Flow Map

Fresh baseline date: 2026-05-20.

Known event-flow areas:
- Campaign launch and send job orchestration.
- Audience resolution and recipient chunking.
- Delivery provider selection, rate control, retries, and feedback handling.
- Tracking open/click/conversion ingestion and analytics publication.
- Automation triggers, schedules, and journey node execution.
- Platform webhooks, notification retries, and integration events.

Rules:
- Keep high-volume Kafka keys shard-aware.
- Keep event envelopes, tenant/workspace headers, idempotency, retries, and DLQ behavior explicit.
- Do not place large audiences in one event, one transaction, or one in-memory list.
