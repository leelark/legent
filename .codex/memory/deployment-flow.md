# Deployment Flow

Fresh baseline date: 2026-05-20.

Current flow:
- Local development uses `docker-compose.yml`, `docker-compose.override.yml`, `config/nginx/nginx.conf`, and `.env.example` for placeholder-safe configuration.
- Production deployment assets live under `infrastructure/kubernetes`, with overlays for environment-specific behavior.
- Release validation is coordinated by `scripts/ops/release-gate.ps1`.

Release evidence required before production-ready claims:
- CI/security scan evidence.
- Image provenance and vulnerability review.
- Route, ingress, and runtime config validation.
- Target-environment egress review.
- Restore/backup proof.
- Monitoring and alert coverage.
- Load evidence for high-volume claims.

Local-only checks are useful preflight evidence, not production approval.
