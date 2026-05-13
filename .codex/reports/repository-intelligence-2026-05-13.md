# Repository Intelligence Report

Date: 2026-05-13.

Sources:

- `git status --short --branch`
- `rg --files | Measure-Object`
- `pom.xml`
- `frontend/package.json`
- `.github/workflows/ci-security.yml`
- `docker-compose.yml`
- `config/gateway/route-map.json`
- `config/nginx/nginx.conf`
- `rg` scans for Kafka listeners, trusted packages, `ddl-auto`, tests, and large files.

Summary:

- Files indexed by `rg --files`: 1153.
- Main file types from read-only agent scan: 663 `.java`, 132 `.tsx`, 95 `.sql`, 76 `.yml`, 48 `.ts`.
- Main ownership by file count: `services` 736, `frontend` 207, `shared` 68, `infrastructure` 55, `scripts` 25.
- Primary stack: Java 21/Spring Boot 3.2.5 Maven multi-module backend plus Next.js 16.2.6/React 19 frontend.
- Runtime: Docker Compose starts PostgreSQL, Redis, Kafka/Zookeeper, MinIO, OpenSearch, ClickHouse, MailHog, 10 services, frontend, and Nginx gateway.
- CI: backend Maven tests, frontend `npm ci`, lint, build, Playwright smoke, npm audit, Compose config, Kustomize render, gitleaks, Trivy.
- Tests found by later consistency review: 58 service Java test files, 15 shared Java test files, and 7 frontend Playwright specs.
- Backend has 93 Flyway SQL migrations across services; largest counts are foundation 15, audience 14, campaign 13, delivery 12. Do not renumber existing Flyway migrations.
- Route map validation passed for 41 routes in read-only agent scan.
- High-volume risks remain open: one resolved audience payload event, default tenant Kafka key, per-recipient render loop, wildcard Kafka trusted packages, `ddl-auto:update` service defaults.
