# Architecture Memory

Fresh baseline date: 2026-05-20.

Source: `AGENTS.md`, `ARCHITECTURE.md`, `PROJECT_CONTEXT.md`, root manifests, and current `.codex` rebuild.

Current architecture facts:
- Legent is a multi-tenant enterprise email marketing, lifecycle automation, deliverability, and analytics platform.
- Frontend is Next.js App Router with React, TypeScript, Tailwind, Zustand, Axios, Chart.js, GSAP/Framer Motion, lucide-react, and Playwright.
- Backend is Java 21 and Spring Boot with service modules for identity, foundation, audience, content, campaign, delivery, tracking, automation, deliverability, and platform.
- Shared backend modules provide common API envelopes, tenant context, security, Kafka, cache, and test support.
- Local runtime uses PostgreSQL, Redis, Kafka/Zookeeper, MinIO, OpenSearch, ClickHouse, MailHog, and Nginx through Compose.
- Production deployment assets live under `infrastructure/kubernetes`.

Operating decisions:
- Preserve service ownership; do not reach across service databases.
- Treat tenant isolation, suppression, unsubscribe, warmup, signed tracking, and inbox safety as product safety boundaries.
- Treat 10 lakh sends in 10 hours as an evidence-backed goal for warmed, authenticated, capacity-approved sending only.
- Use `.codex/bootstrap.md` as the entry point for autonomous engineering sessions.
