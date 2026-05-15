# Architecture Memory

Last updated: 2026-05-16.

- 2026-05-13, source `pom.xml`, `frontend/package.json`, `docker-compose.yml`: Legent is a Java 21/Spring Boot Maven multi-module backend plus Next.js 16 frontend with PostgreSQL, Redis, Kafka, MinIO, OpenSearch, ClickHouse, MailHog, Nginx, Compose, and Kubernetes overlays. Impact: production-readiness work must validate service, event, DB, frontend, and deployment boundaries.
- 2026-05-13, source `AGENTS.md`: Product must never promise guaranteed inbox placement or unsafe 10 lakh sends from new domains/new addresses. Impact: warmup, DNS auth, suppression, provider capacity, engagement, rate control, queue sharding, observability must gate scale.
- 2026-05-16, source `docker-compose.yml`, service `application.yml`, Kubernetes base configmap: non-test runtime defaults now use `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, Flyway baseline disabled, Flyway validation enabled, and out-of-order disabled unless explicitly overridden. Impact: schema drift fails closed; production still must use reviewed overlays and External Secrets.
- 2026-05-16, source `.codex` sweep and touched services: event-driven reliability now depends on completion-based idempotency for more consumers. Foundation bootstrap awaits critical publishes; automation and deliverability use pending idempotency claims; audience intelligence no longer swallows parse/persistence failures. Impact: future consumers should use claim/mark/release or transactional outbox patterns rather than marking processed before side effects finish.
