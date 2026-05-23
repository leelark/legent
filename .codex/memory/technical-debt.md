# Technical Debt

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Completed debt rows were removed from current-state memory. Detailed completion history remains in .codex/backlog/queue.json doneWork and checkpoints.

## Live Technical Debt Themes

| Area | Current debt | Queue items |
|---|---|---|
| High-volume audience/campaign | Audience resolution now uses local metadata-only Kafka chunk references backed by audience-owned durable JSONB chunk rows; content references and target runtime/load proof still need evidence before scale claims. | `campaign-content-reference-target-proof`, `live-high-volume-proof` |
| Delivery hot path | Immutable policy snapshots, send-governance policy management/selection UI, feedback outbox terminal retention cleanup, and Campaign Wizard provider/domain selectors are locally implemented; rate reservations, deeper auth-check evidence, and dedicated policy audit-history APIs still need further hardening/live evidence. | `delivery-rate-control-sharded-reservations` |
| Kafka/retry/DLQ | Local topic coverage exists, but retry/DLQ target readiness and broker evidence remain incomplete. | `retry-dlq-target-readiness` |
| Contact/segment parity | Contact relationships, retention audit, and Segment Builder v2 are incomplete; import-time contact/data-extension provenance population and frontend audience import parser/modal polish are locally implemented. | `contact-builder-relationship-cardinality`, `contact-retention-deletion-audit`, `segment-builder-v2-*` |
| Automation depth | Activity locking is locally implemented; file movement, advanced nodes, artifact selectors, replay evidence, and script sandbox remain incomplete. | `automation-live-file-movement-storage-adapter`, `journey-advanced-node-handlers-contract`, `automation-artifact-selector-ux`, blocked automation items |
| AI/provider runtime | Provider contract/metering ledgers, reviewed AI draft apply UX, and tightened public/admin claim-boundary copy exist locally; live provider invocation, cross-service audit verification, STO/frequency runtime, and generated segment/workflow previews remain incomplete. | AI runtime/backlog items |
| QA maturity | Backend Failsafe integration-test profile, conservative backend/frontend coverage gates, full Chromium Playwright script/CI gate, and local full Chromium run evidence exist; target login smoke, Compose health transcript, and Docker-capable runtime IT proof remain incomplete. | `qa-*` |
| Release evidence | Deployment Manager can now track local-vs-strict evidence references, but strict production evidence and image/egress/GA evidence are still absent. | release BLOCKED items |

Selection source of truth: .codex/backlog/queue.json.
