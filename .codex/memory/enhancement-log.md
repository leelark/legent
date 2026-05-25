# Enhancement Log

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Current enhancement source of truth: .codex/backlog/queue.json and .codex/reports/100-percent-readiness-backlog-2026-05-23.md.

## Active Enhancement Themes

- Salesforce/competitor parity: official Salesforce, Adobe, Braze, Klaviyo, and HubSpot source freshness was refreshed on 2026-05-24; Segment Builder v2 taxonomy, execution plans, and local governance UI, Contact Builder relationship/provenance/retention, the first Journey Builder `WAIT_UNTIL` runtime node, and Foundation-owned package export/import dry-run validation are implemented locally; persisted segment governance/audit APIs, broader Journey Builder node depth, Automation Studio activity depth, model-backed AI, and live package apply remain open.
- AI maturity: provider policy, metering, kill switch, reviewed draft workflow, public/admin claim-boundary copy, STO runtime, frequency runtime, and provider-free segment/workflow generation previews are locally implemented; model-backed generation, frontend apply UX, and target runtime evidence remain open.
- Operator UX: Campaign provider/domain selectors, audience import parser/modal polish, audience metadata-only chunk handoff, send-governance policy UI, Deployment Manager evidence attachments, and Automation Studio governed selector UX for data extensions, users, campaigns, and artifact get-by-id verification are locally implemented; a true artifact browse/search picker remains open until automation-service exposes a list endpoint.
- Analytics contract: tracking operational analytics now separate canonical event-id counts from physical raw-row exports/reconciliation diagnostics; live ClickHouse/PostgreSQL proof and funnel/segment semantics remain open.
- Production readiness: signed internal identity is locally implemented for one campaign-to-audience chunk read path, and Alertmanager receiver handoff backing is locally guarded; strict evidence pack, image/egress/GA evidence, provider capacity, high-volume proof, target auth/runtime evidence, and platform service identity remain open.
- QA/CI: integration-test phase, coverage gates, full Playwright Chromium gate, and visual shell smoke gate with CI screenshot artifacts are locally implemented; target login smoke and Compose health transcript remain open.

Completed enhancement narratives were removed from this file and remain preserved in queue doneWork, checkpoints, and audit events.
