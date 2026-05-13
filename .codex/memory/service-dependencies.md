# Service Dependencies

Last updated: 2026-05-13.

- Identity signup publishes tenant provisioning events consumed by foundation.
- Identity password reset publishes `email.send.requested` for delivery/content email handoff.
- Foundation consumes identity signup for tenant provisioning and publishes tenant bootstrap events.
- Campaign publishes audience resolution requests to audience, receives resolved audience chunks, creates send batches, then publishes email send requests to delivery.
- Audience resolves lists/segments/subscribers, calls deliverability suppression check, and publishes `send.audience.resolved`.
- Delivery consumes `email.send.requested`, publishes sent/failed/bounced/retry events.
- Tracking ingests signed open/click/conversion, writes PostgreSQL/ClickHouse, and publishes `tracking.ingested`.
- Deliverability consumes bounce/complaint feedback and owns DNS/reputation/DMARC state.
- Platform owns webhooks, notifications, OpenSearch indexing, and related admin routes.
- Automation publishes workflow-triggered send actions into campaign/delivery topics.
