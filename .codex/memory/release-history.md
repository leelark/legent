# Release History

Last updated: 2026-05-13.

- No release performed.
- 2026-05-13 orchestration setup is repo-process only; no product code shipped.
- 2026-05-13 release-hardening change: non-test service/Kubernetes base JPA DDL defaults now validate instead of update. Not released; validation limited to `rg` config check and `docker compose config --quiet`.
- 2026-05-13 release-hardening change: local Compose health validation now aligns with frontend host port and Redis optional auth. Not released; validation limited to parser and Compose config checks.
- 2026-05-13 security-hardening change: Kafka JSON deserialization trust narrowed in shared config and service YAMLs. Not released; validation limited to shared Kafka Maven tests and grep scans.
- 2026-05-13 security-hardening change: shared `TenantFilter` rejects workspace/environment header conflicts against JWT context and preserves authenticated context when headers are omitted. Not released; validation limited to shared security Maven tests.
- 2026-05-13 parallel hardening change: audience resolved events are chunked, high-volume Kafka publisher keys avoid tenant fallback, and delivery send-request consumer failures rethrow after logging. Not released; validation limited to integrated Maven test for shared Kafka/security plus audience/delivery services.
- 2026-05-13 reliability-hardening change: shared Kafka listener error handling now publishes exhausted retries to declared central `kafka.dead-letter`, and service Kafka consumers rethrow unexpected processing failures after logging while preserving explicit invalid/malformed-event drops. Not released; validation limited to impacted Maven tests.
- 2026-05-13 audience-service hardening change: audience resolution now pages all-subscriber candidates by keyset cursor, checks suppressions per page, publishes chunks with one-pass lookahead last-chunk semantics, and fails closed by rethrowing suppression/client/publish failures. Not released; validation limited to impacted Maven tests.
- 2026-05-13 production-infra hardening change: production overlay renders pinned `legent/*:1.0.2` images, production ingress hosts, `CLICKHOUSE_DB=legent_analytics`, and `/ws/analytics` routing to tracking-service; release gate now runs production overlay drift checks. Not released; validation limited to route map, production overlay, and Kustomize release-gate slice.
- 2026-05-13 tracking/frontend integration hardening change: authenticated analytics WebSocket now preserves workspace context across refresh and uses API-base WebSocket origin; production CORS allows workspace/environment headers. Not released; validation limited to focused Maven tests and frontend lint.
- 2026-05-13 final validation change: local runtime health validation and gateway WebSocket route were fixed after full Compose startup exposed drift. Not released; validation passed across full Maven clean compile/test/package, frontend lint/build/Playwright smoke, Docker Compose build/start/health, route validation, production overlay validation, release-gate infra slice, Nginx config test, and recent log scan.
