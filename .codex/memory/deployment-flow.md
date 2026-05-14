# Deployment Flow

Last updated: 2026-05-14.

Sources: `.github/workflows/ci-security.yml`, `docker-compose.yml`, `infrastructure/kubernetes/**`, `scripts/ops/release-gate.ps1`.

- 2026-05-14 final validation: `docker compose config --quiet`, `docker compose build`, `docker compose up -d`, `scripts/ops/validate-compose-health.ps1`, direct gateway/frontend health probes, and recent Compose log error scan passed. All app services reported healthy; frontend host port was 3003 from live Compose metadata.
- 2026-05-14 release-gate infra slice: route validation and Kustomize overlay render passed; production overlay drift check failed intentionally on inherited broad egress.
- CI backend job runs `./mvnw test -DskipITs`.
- CI frontend job runs `npm ci`, `npm run lint`, `npm run build:ci`, Playwright Chromium smoke, and `npm audit --omit=dev --audit-level=high`.
- CI compose smoke runs `docker compose config --quiet` and Kustomize production render.
- CI security runs gitleaks and Trivy high/critical scan.
- Local runtime uses Compose with service healthchecks and gateway dependency on healthy services.
- Production shape uses Kubernetes base plus production overlay with external secrets and removal of local/nonprod stateful resources.
- 2026-05-13 read-only agent ran `scripts\ops\validate-route-map.ps1`; result passed for 41 routes.
- 2026-05-13 main thread validation: `scripts\ops\validate-route-map.ps1` passed for 41 routes; `docker compose config --quiet` exited 0.

Open deployment drift from 2026-05-14 runtime/container scan:

- Production overlay validation fails closed because production still inherits broad base `allow-legent-egress` with `0.0.0.0/0` TCP 443. Exact provider CIDRs/ports or CNI FQDN-policy support are needed before replacing it.
- Production ingress hosts and image tags were hardened on 2026-05-13; keep release validators in CI/release gate to prevent regression.
- Compose service Dockerfiles copy prebuilt `target/*-SNAPSHOT.jar`; clean checkout needs Maven package before `docker compose up -d --build`.
- Resolved 2026-05-13: `scripts/ops/validate-compose-health.ps1` now probes `FRONTEND_HOST_PORT` or 3000.
- Resolved 2026-05-13: Redis Compose healthcheck now authenticates with `REDISCLI_AUTH` when `REDIS_PASSWORD` is present.
- Observability Kustomize omits `prometheus-alerts.yml`; OTEL exporter currently logs traces only.
- Resolved 2026-05-13: production overlay sets `CLICKHOUSE_DB=legent_analytics`.
- Resolved 2026-05-14: service and Kubernetes base Flyway/DDL defaults are safe by default (`ddl-auto=validate`, Flyway baseline false, validate true, out-of-order false). Residual: Kubernetes base still includes local/nonprod dependencies such as MailHog/local URLs and must not be used as production without the production overlay.
- Resolved 2026-05-14: CI/release gates now include sanitizer regression, route validation, and production overlay validation. Residual: CI still does not build Docker images or run Compose health by default.
