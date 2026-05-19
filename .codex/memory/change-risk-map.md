# Change Risk Map

Fresh baseline date: 2026-05-20.

High-risk change areas:
- Auth, cookies, refresh sessions, SCIM, tenant/workspace context, and unsafe-method guards.
- Campaign launch, audience resolution, suppression checks, delivery handoff, provider selection, warmup, and retries.
- Kafka topics, keys, event envelopes, retry/DLQ behavior, and deserialization trust.
- Tracking open/click/conversion signing, ingestion, outbox, and analytics aggregation.
- Route ownership across gateway map, Nginx, Kubernetes ingress, and frontend API client usage.
- Flyway migrations and production database behavior.
- CI, release gates, image provenance, egress policy, and Kubernetes overlays.

Default mitigation:
- Assign security/performance/release partners through `.codex/agents/routing-matrix.md`.
- Create a checkpoint before multi-file changes.
- Run the narrowest meaningful gate plus any required cross-boundary validator.
