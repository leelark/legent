# Production Readiness Audit

Date: 2026-05-20

Status: local audit complete. Public production readiness remains blocked by external evidence.

## Validation Run

- `powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1`: passed.
- `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1`: passed for 49 routes.
- `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1`: passed.
- `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1`: passed with warning about placeholder-like ExternalSecret text.
- `docker compose config --quiet`: passed.
- `kubectl kustomize infrastructure\kubernetes\overlays\production`: rendered.
- `.\mvnw.cmd -pl services/foundation-service -am test`: passed after the feature flag tenant-isolation fix.

## Completed Fix

- `feature-flag-tenant-scoped-id-operations`: `GET /api/v1/feature-flags/{id}` now requires `feature:read`, and feature flag get/update/delete by ID use tenant-scoped lookup through `findByIdAndTenantIdAndDeletedAtIsNull`.
- Tests added for controller authorization metadata, tenant-scoped reads, cross-tenant not-found behavior, missing tenant fail-closed behavior, and update/delete cross-tenant denial.
- `internal-routes-edge-blocking`: public Nginx and Kubernetes ingress now return 404 for known service-internal content, audience, and deliverability routes.
- `validate-route-map.ps1` now enforces those public-edge deny rules so future route drift is caught locally.

## Key Findings

- Security: delivery provider configuration is tenant-scoped rather than workspace-scoped. This needs a migration-safe design before implementation.
- Security: service-internal endpoints were publicly routable under broad prefixes and relied on shared `X-Internal-Token` checks. Public-edge deny rules are now in place for the audited internal endpoints.
- Security: SSO sets the tenant cookie with weaker `httpOnly` posture than normal auth.
- Kafka/reliability: delivery feedback publication is fire-and-forget around message state changes and needs durable outbox-style handling.
- Kafka/reliability: shared DLQ routing sends all failed records to `kafka.dead-letter` partition 0.
- Performance: 10 lakh sends in 10 hours remains blocked by code gaps and missing target-like evidence. Findings include broad suppression fetches during audience resolution, per-message pessimistic rate reservations, single-record tracking consumers, unpaged/global retry scans, and lack of live load proof.
- Release: strict production release remains blocked by missing target evidence for image provenance, egress, live load, restore, monitoring, TLS/admission, and CI/security transcripts.
- Release validator gap: the production egress evidence validator accepted the template file unexpectedly; this is queued as a READY fix.

## Backlog Outcome

- READY items added for bulk suppression checks, delivery feedback durability, campaign content reference contract alignment, egress evidence validator hardening, Kafka DLQ sharding, SSO cookie posture, tracking ingress rate policy, and Salesforce parity refresh.
- BACKLOG items added for delivery provider workspace isolation and platform event idempotency.
- BLOCKED items retained for strict production evidence and live high-volume proof.
