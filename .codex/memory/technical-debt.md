# Technical Debt

Last updated: 2026-05-16.

Open:

- Large frontend route/components over 700-1200 lines make UI changes risky: `PublicPageView.tsx`, workspace template editor, admin/settings consoles, campaign creation page. Admin/settings visual chrome was aligned on 2026-05-16, but the consoles remain large and should still be split by panel/domain when next touched.
- Large backend services over 600-990 lines concentrate too many responsibilities: foundation global/core platform, identity federation, campaign launch orchestration, delivery orchestration.
- Docs/scripts may drift around ports and validation flags; verify scripts before relying on docs.
- 2026-05-16 cleanup removed dependency-proven unreachable frontend tracking/content/admin/layout files, `useFeatureFlag`, old tracking frontend API/WebSocket wrappers, dead `TenantException`, and an unused delivery fallback method. Keep future deletion work dependency-driven rather than filename-driven.
- Generated `target/classes/db/migration` directories exist locally; do not modify generated outputs.
- Kafka consumer error handling debt was reduced on 2026-05-13 with shared retry/DLQ wiring and owned listener rethrow behavior. Remaining debt: weak `EventEnvelope<?>` / `EventEnvelope<Object>` payload contracts still need schema validation and service-level contract tests.
- Frontend workspace shell has high blast radius: session hydration, tenant bootstrap, preferences, redirect, shell chrome, and toasts are coupled in one layout.
- Tenant/workspace metadata can drift because layout, header, auth views, context bootstrap, auth store, and tenant store all write context. Tokens are not stored in localStorage, but non-secret context still needs consistency tests.
- Flyway version gaps exist in identity, tracking, and automation. Flyway allows gaps, but audit docs should note them to avoid future renumbering.
