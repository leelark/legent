# Architecture Memory

Last updated: 2026-05-13.

- 2026-05-13, source `pom.xml`, `frontend/package.json`, `docker-compose.yml`: Legent is a Java 21/Spring Boot Maven multi-module backend plus Next.js 16 frontend with PostgreSQL, Redis, Kafka, MinIO, OpenSearch, ClickHouse, MailHog, Nginx, Compose, and Kubernetes overlays. Impact: production-readiness work must validate service, event, DB, frontend, and deployment boundaries.
- 2026-05-13, source `AGENTS.md`: Product must never promise guaranteed inbox placement or unsafe 10 lakh sends from new domains/new addresses. Impact: warmup, DNS auth, suppression, provider capacity, engagement, rate control, queue sharding, observability must gate scale.
- 2026-05-13, source `docker-compose.yml`: Local Compose sets `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, but service application files default to `update` when env missing. Impact: production must force validate/Flyway; open risk recorded in `security-findings.md`.
