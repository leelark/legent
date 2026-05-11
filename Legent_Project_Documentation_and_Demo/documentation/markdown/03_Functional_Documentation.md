# Functional Documentation

## Audience And Personas

| Persona | Goal | Main Modules |
| --- | --- | --- |
| Lifecycle marketer | Build audiences, templates, campaigns, journeys | Audience, Email, Campaign, Automation |
| Marketing operations | Govern launch readiness, approval, recovery | Campaign, Delivery, Admin |
| Deliverability owner | Protect sender health and inbox placement | Deliverability, Delivery, Tracking |
| Admin/operator | Configure tenant, roles, runtime controls | Identity, Foundation, Admin, Platform |
| Developer/SRE | Run and observe platform services | Infrastructure, Tests, Operations |

## Functional Module Matrix

| Service | Port | Database | Controllers | Entities | Migrations | Responsibility |
| --- | --- | --- | --- | --- | --- | --- |
| audience-service | 8082 | ${DB_NAME:legent_audience | 9 | 12 | 13 | Subscribers, lists, segments, imports, consent, suppressions, preferences. |
| automation-service | 8086 | ${DB_NAME:legent_automation | 2 | 5 | 3 | Workflow definitions, graph validation, schedules, runs, simulations. |
| campaign-service | 8083 | ${DB_NAME:legent_campaign | 5 | 17 | 13 | Campaigns, audiences, approvals, experiments, budgets, frequency, send jobs. |
| content-service | 8090 | ${DB_NAME:legent_content | 8 | 14 | 7 | Email templates, content blocks, assets, landing pages, test sends, approvals. |
| deliverability-service | 8087 | ${DB_NAME:legent_deliverability | 5 | 6 | 7 | Sender domains, DNS verification, reputation, spam scoring, DMARC, suppression. |
| delivery-service | 8084 | ${DB_NAME:legent_delivery | 2 | 12 | 11 | Provider routing, queue operations, replay, warmup, rate limits, safety evaluation. |
| foundation-service | 8081 | ${DB_NAME:legent_foundation | 15 | 11 | 11 | Tenants, feature flags, branding, admin configuration, bootstrap, public content. |
| identity-service | 8089 | ${DB_NAME:legent_identity | 2 | 11 | 8 | Authentication, sessions, account membership, onboarding state, preferences. |
| platform-service | 8088 | ${DB_NAME:legent_platform | 4 | 6 | 4 | Notifications, webhooks, search indexing, tenant integration utilities. |
| tracking-service | 8085 | ${DB_NAME:legent_tracking | 4 | 3 | 7 | Open/click ingestion, analytics summaries, funnels, websocket analytics. |

## End-To-End User Journey

1. Visitor opens public site and reviews product capabilities.
2. Visitor signs up and creates workspace context.
3. User completes onboarding for workspace, sender, and provider readiness.
4. User imports or creates audience subscribers.
5. User creates lists, segments, and data extensions.
6. User prepares templates, content, and landing pages.
7. User creates campaign, selects content and audience, sets frequency/budget/experiment controls.
8. User runs launch readiness and confirms launch.
9. Campaign service coordinates send jobs and audience resolution.
10. Delivery service routes messages through providers.
11. Tracking service records opens/clicks/events.
12. Analytics, deliverability, and admin screens expose operational state.

## Acceptance Notes

- Workspace APIs must have tenant/workspace context.
- Campaign launch must pass readiness checks.
- Delivery execution should remain idempotent and replay-safe.
- Tracking endpoints must ingest events without breaking public tracking pixels/clicks.
- Admin settings must be auditable and scoped.
