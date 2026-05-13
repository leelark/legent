# Deployment Flow

Last updated: 2026-05-13.

Sources: `.github/workflows/ci-security.yml`, `docker-compose.yml`, `infrastructure/kubernetes/**`, `scripts/ops/release-gate.ps1`.

- CI backend job runs `./mvnw test -DskipITs`.
- CI frontend job runs `npm ci`, `npm run lint`, `npm run build:ci`, Playwright Chromium smoke, and `npm audit --omit=dev --audit-level=high`.
- CI compose smoke runs `docker compose config --quiet` and Kustomize production render.
- CI security runs gitleaks and Trivy high/critical scan.
- Local runtime uses Compose with service healthchecks and gateway dependency on healthy services.
- Production shape uses Kubernetes base plus production overlay with external secrets and removal of local/nonprod stateful resources.
- 2026-05-13 read-only agent ran `scripts\ops\validate-route-map.ps1`; result passed for 41 routes.
- 2026-05-13 main thread validation: `scripts\ops\validate-route-map.ps1` passed for 41 routes; `docker compose config --quiet` exited 0.

Open deployment drift from 2026-05-13 runtime/container scan:

- Production ingress hosts are still `api.legent.local` and `app.legent.local`, while production config advertises `https://api.legent.com`.
- Production egress policy may block managed DB/Redis/Kafka/ClickHouse/SMTP endpoints because base egress allows same-namespace pods, DNS, and public 443 only, while production adds default deny.
- Kubernetes app images use mutable `:latest`; production does not pin tags/digests.
- Compose service Dockerfiles copy prebuilt `target/*-SNAPSHOT.jar`; clean checkout needs Maven package before `docker compose up -d --build`.
- Resolved 2026-05-13: `scripts/ops/validate-compose-health.ps1` now probes `FRONTEND_HOST_PORT` or 3000.
- Resolved 2026-05-13: Redis Compose healthcheck now authenticates with `REDISCLI_AUTH` when `REDIS_PASSWORD` is present.
- Observability Kustomize omits `prometheus-alerts.yml`; OTEL exporter currently logs traces only.
- ClickHouse DB differs: Compose uses `legent_analytics`, Kubernetes base config uses `default`.
- Kubernetes base config is not production-safe by itself: Flyway validation disabled, out-of-order enabled, MailHog, local URLs. `ddl-auto` was hardened to `validate` on 2026-05-13. Production patch corrects several values.
- CI does not run route-map validator and does not build Docker images; release gate includes route validation but not Compose health by default.
