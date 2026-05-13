# Ownership Map

Last updated: 2026-05-13.

- Identity/auth/session: `services/identity-service`, shared auth in `shared/legent-security`, frontend clients in `frontend/src/lib/auth*.ts`.
- Tenant/workspace/environment/admin config: `services/foundation-service`, route prefixes `/api/v1/configs`, `/feature-flags`, `/tenants`, `/core`, `/admin`, `/public`, `/health`, `/audit-logs`, `/compliance`.
- Audience and suppression records: `services/audience-service`, route prefixes `/api/v1/subscribers`, `/segments`, `/imports`, `/lists`, `/data-extensions`, `/suppressions`.
- Content/rendering/assets: `services/content-service`, route prefixes `/api/v1/templates`, `/content`, `/assets`, `/emails`, `/personalization-tokens`, `/brand-kits`, `/landing-pages`, `/public/landing-pages`.
- Campaign/send orchestration: `services/campaign-service`, route prefixes `/api/v1/campaigns`, `/send-jobs`.
- Delivery/provider handoff: `services/delivery-service`, route prefixes `/api/v1/providers`, `/delivery`.
- Tracking/analytics: `services/tracking-service`, route prefixes `/api/v1/tracking`, `/analytics`, `/events`.
- Automation workflows: `services/automation-service`, route prefixes `/api/v1/workflows`, `/workflow-definitions`, `/automation-studio`.
- Deliverability reputation/DMARC/domains: `services/deliverability-service`, route prefixes `/api/v1/deliverability`, `/reputation`, `/dmarc`.
- Platform webhooks/search/notifications: `services/platform-service`, route prefixes `/api/v1/platform`, `/api/v1/admin/webhooks`, `/api/v1/admin/search`.
