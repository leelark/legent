# Change Risk Map

Last updated: 2026-05-13.

High risk:

- Tenant/workspace context filters, JWT/session cookies, origin guard, SCIM token validation.
- Kafka envelope, partition keys, listener error behavior, idempotency, DLQ/retry paths.
- Audience resolution and campaign batching/send execution.
- Delivery provider selection, warmup/rate limits, suppression, bounce/complaint handling.
- Tracking signed URL verification, outbox, ClickHouse writes.
- Route ownership across route-map, Nginx, and Kubernetes ingress.
- Flyway migrations and production `ddl-auto` behavior.

Medium risk:

- Frontend workspace layout, auth store, tenant store, API client.
- Admin/platform consoles with large component state.
- Build scripts and cached Docker build flows.

Low risk:

- Documentation-only updates when they do not claim production capability beyond validated behavior.
