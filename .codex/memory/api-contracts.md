# API Contracts

Fresh baseline date: 2026-05-20.

Source: `AGENTS.md`, `PROJECT_CONTEXT.md`, `config/gateway/route-map.json`, `config/nginx/nginx.conf`.

Current contract facts:
- Public and workspace routes are owned by service boundaries in `config/gateway/route-map.json`.
- Route changes must keep gateway route map, Nginx locations, and Kubernetes ingress synchronized.
- Backend API responses should use existing shared response envelope patterns.
- Tenant/workspace/environment context headers must be preserved for tenant-scoped operations.
- Public routes must be intentionally public, documented, and tested.

Open follow-up:
- Run `.codex/commands/full-audit.md` before changing route ownership or API contracts.
