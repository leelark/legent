# Salesforce And Competitor Parity Matrix

Fresh baseline date: 2026-05-20.

This matrix is a living research artifact. Use `.codex/commands/research-pass.md` to refresh it with current official or primary sources before making product claims.

| Area | Market Capability To Verify | Current Legent Source | Gap Status | Backlog Link |
|---|---|---|---|---|
| Email Studio | Templates, blocks, dynamic content, personalization, validation, test sends, approvals. | `services/content-service/`, `frontend/`, `PROJECT_CONTEXT.md` | Needs fresh audit. | `salesforce-parity-research-refresh` |
| Contact Builder | Subscribers, data extensions, preferences, suppressions, imports, segments, relationships. | `services/audience-service/`, `services/deliverability-service/` | Needs fresh audit. | `salesforce-parity-research-refresh` |
| Journey Builder | Triggers, waits, decisions, goals, exits, versioning, simulation, monitoring. | `services/automation-service/`, `frontend/` | Needs fresh audit. | `salesforce-parity-research-refresh` |
| Automation Studio | Schedules, imports, query activities, extracts, send automation, file/object storage. | `services/automation-service/`, `services/platform-service/` | Needs fresh audit. | `salesforce-parity-research-refresh` |
| Deliverability | DNS auth, DMARC, warmup, suppressions, FBL, reputation, safety checks. | `services/deliverability-service/`, `services/delivery-service/` | Evidence required. | `high-volume-readiness-audit` |
| Analytics | Campaign, journey, provider, engagement, deliverability, attribution, anomaly detection. | `services/tracking-service/`, ClickHouse docs/config | Needs fresh audit. | `fresh-repo-production-audit` |
| AI | Content assistance, send-time optimization, predictive segments, frequency optimization. | Project context and implementation audit required. | Needs research. | `salesforce-parity-research-refresh` |
| Enterprise/Admin | RBAC, environments, approvals, audit, SSO/SCIM, release evidence, governance. | `services/foundation-service/`, `services/identity-service/`, `.codex` | Needs fresh audit. | `fresh-repo-production-audit` |

Research rules:
- record source URL and date in `docs/product/competitor-research/`,
- separate source facts from product inference,
- convert gaps into `.codex/backlog/queue.json` work items.
