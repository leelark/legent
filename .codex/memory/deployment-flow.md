# Deployment Flow

Last updated: 2026-05-16.

Sources: `.github/workflows/ci-security.yml`, `docker-compose.yml`, `infrastructure/kubernetes/**`, `scripts/ops/release-gate.ps1`.

- 2026-05-14 final validation: `docker compose config --quiet`, `docker compose build`, `docker compose up -d`, `scripts/ops/validate-compose-health.ps1`, direct gateway/frontend health probes, and recent Compose log error scan passed. All app services reported healthy; frontend host port was 3003 from live Compose metadata.
- 2026-05-16 release gate: full `scripts/ops/release-gate.ps1` passed locally, including backend Maven clean package, frontend lint/sanitize/build/smoke/visual, Compose config, Kubernetes renders, and production overlay drift checks.
- 2026-05-16 production egress update: production overlay deletes inherited broad base egress and renders default-deny plus internal/DNS-only egress. Live external egress policy still needs reviewed provider/managed-service CIDRs/ports or approved CNI FQDN policy before promotion.
- CI backend job runs `./mvnw test -DskipITs`.
- CI frontend job runs `npm ci`, `npm run lint`, `npm run build:ci`, Playwright Chromium smoke, and `npm audit --omit=dev --audit-level=high`.
- CI compose smoke runs `docker compose config --quiet` and Kustomize production render.
- CI security runs gitleaks and Trivy high/critical scan.
- Local runtime uses Compose with service healthchecks and gateway dependency on healthy services.
- Production shape uses Kubernetes base plus production overlay with external secrets and removal of local/nonprod stateful resources.
- 2026-05-13 read-only agent ran `scripts\ops\validate-route-map.ps1`; result passed for 41 routes.
- 2026-05-13 main thread validation: `scripts\ops\validate-route-map.ps1` passed for 41 routes; `docker compose config --quiet` exited 0.

Open deployment drift from 2026-05-16 runtime/container scan:

- Production overlay validation passes locally, but live production egress remains incomplete until exact provider/managed-service CIDRs/ports or CNI FQDN-policy support are reviewed and encoded.
- Production ingress hosts and image tags were hardened on 2026-05-13; keep release validators in CI/release gate to prevent regression.
- Compose service Dockerfiles copy prebuilt `target/*-SNAPSHOT.jar`; clean checkout needs Maven package before `docker compose up -d --build`.
- Resolved 2026-05-16: `scripts/ops/validate-compose-health.ps1` resolves the frontend host port from live `docker compose ps --format json` publishers first, then legacy port text, then `FRONTEND_HOST_PORT`, then default 3000. Source: `scripts/ops/validate-compose-health.ps1`; impact: local health validation no longer assumes a stale frontend host port when Compose publishes another host port such as 3003.
- Resolved 2026-05-13: Redis Compose healthcheck now authenticates with `REDISCLI_AUTH` when `REDIS_PASSWORD` is present.
- Observability Kustomize omits `prometheus-alerts.yml`; OTEL exporter currently logs traces only.
- Resolved 2026-05-13: production overlay sets `CLICKHOUSE_DB=legent_analytics`.
- Resolved 2026-05-14: service and Kubernetes base Flyway/DDL defaults are safe by default (`ddl-auto=validate`, Flyway baseline false, validate true, out-of-order false). Residual: Kubernetes base still includes local/nonprod dependencies such as MailHog/local URLs and must not be used as production without the production overlay.
- Resolved 2026-05-14: CI/release gates now include sanitizer regression, route validation, and production overlay validation. Residual: CI still does not build Docker images or run Compose health by default.
