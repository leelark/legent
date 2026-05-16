# Docs/Scripts Drift Hygiene - 2026-05-16

Scope: `.codex/memory/**`, `.codex/reports/**`, `docs/**`, and non-infra-overlapping script validation.

Validated command surfaces without reading `.env` secrets:

- `scripts/ops/release-gate.ps1`: flags include `-RunSyntheticSmoke` and `-SmokeBaseUrl`; `-SmokeBaseUrl` defaults from `LEGENT_SMOKE_BASE_URL`.
- `scripts/ops/synthetic-smoke.ps1`: `-BaseUrl` defaults to `http://localhost:8080` and probes `/api/v1/health`, protected campaign/subscriber reads, and unsafe cross-site write rejection.
- `scripts/load/phase3-high-volume-load.ps1`: default `-BaseUrl` remains `http://localhost:8080/api/v1`; `-RequireLive` rejects dry runs, missing tokens, synthetic dataset names, placeholder campaign/data-extension IDs, and enabled mock import scenarios.
- `scripts/ops/backup-restore.ps1`: `-OutputDir` is optional; backups default to `LEGENT_BACKUP_DIR`, `$HOME/legent-backups`, or temp fallback, and repo-local backup output is refused except ignored `.\backups`.
- `scripts/ops/validate-compose-health.ps1`: frontend health port resolves from live Compose publishers, then legacy port text, then `FRONTEND_HOST_PORT`, then default 3000; gateway health remains `http://127.0.0.1:8080/api/v1/health`.

Fixes made:

- Updated `docs/security/csrf-cookie-threat-model.md` so cookie-authenticated unsafe requests without `Origin` or `Referer` are documented as fail-closed, while non-cookie service clients remain allowed.
- Updated `.codex/memory/deployment-flow.md` to remove stale Compose health port wording.
- Removed the generic docs/scripts drift item from `.codex/memory/unresolved-risks.md` and recorded this pass as resolved. External blockers remain unresolved.

Residual risks:

- Live target synthetic smoke, high-volume load, restore drill, CI gitleaks/Trivy transcript, and monitoring handoff evidence remain required before GA.
- Exact production external egress CIDRs/ports or approved FQDN-policy model remain external blockers and were not marked resolved.
