# API Contracts

Last updated: 2026-05-13.

Source: `config/gateway/route-map.json`, `config/nginx/nginx.conf`.

- Public gateway base is Nginx on port 8080 with `/api/v1/*` proxying to owning services.
- Route-map and Nginx include domain prefixes for identity, foundation, audience, content, campaign, delivery, automation, tracking, deliverability, and platform.
- `config/nginx/nginx.conf` adds rate limits: `api_limit` 100 r/s and `tracking_limit` 200 r/s.
- `/api/v1/track` returns 410; tracking uses `/api/v1/tracking`.
- Public routes: `/api/v1/public`, `/api/v1/public/landing-pages`, `/api/v1/health`, plus signed tracking routes as documented in security rules.
- Contract risk: route-map, Nginx, and Kubernetes ingress must remain synchronized when route ownership changes.

Route-map gaps found 2026-05-13:

- Active controller roots not mapped: `/api/v1/preferences`, `/api/v1/audience`, `/api/v1/differentiation`, `/api/v1/global`, `/api/v1/performance-intelligence`, `/api/v1/federation`, `/api/v1/scim/v2`, `/api/v1/sso`.
- Mapped `/api/v1/events` appears unused by current tracking controllers.
- Next action: confirm intended public/gateway exposure, then update route-map, Nginx, and ingress together with route validation.
