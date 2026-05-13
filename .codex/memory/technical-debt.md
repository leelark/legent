# Technical Debt

Last updated: 2026-05-13.

Open:

- Large frontend route/components over 700-1200 lines make UI changes risky: `PublicPageView.tsx`, workspace template editor, admin/settings consoles, campaign creation page.
- Large backend services over 600-990 lines concentrate too many responsibilities: foundation global/core platform, identity federation, campaign launch orchestration, delivery orchestration.
- Docs/scripts may drift around ports and validation flags; verify scripts before relying on docs.
- Generated `target/classes/db/migration` directories exist locally; do not modify generated outputs.
- Kafka consumer error handling debt: delivery, tracking, platform, deliverability, and possibly automation consumers need explicit retry/DLQ behavior instead of catch/log/return.
- Frontend workspace shell has high blast radius: session hydration, tenant bootstrap, preferences, redirect, shell chrome, and toasts are coupled in one layout.
- Tenant/workspace metadata can drift because layout, header, auth views, context bootstrap, auth store, and tenant store all write context. Tokens are not stored in localStorage, but non-secret context still needs consistency tests.
- Flyway version gaps exist in identity, tracking, and automation. Flyway allows gaps, but audit docs should note them to avoid future renumbering.
