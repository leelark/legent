# Technical Debt

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Completed debt rows were removed from current-state memory. Detailed completion history remains in .codex/backlog/queue.json doneWork and checkpoints.

## Live Technical Debt Themes

| Area | Current debt | Queue items |
|---|---|---|
| High-volume audience/campaign | Audience chunks and content references still need metadata-only/runtime proof before scale claims. | `audience-resolution-metadata-only-chunks`, `campaign-content-reference-target-proof` |
| Delivery hot path | Rate reservations and feedback outbox retention need further hardening. | `delivery-rate-control-sharded-reservations`, `delivery-feedback-outbox-retention-cleanup` |
| Kafka/retry/DLQ | Local topic coverage exists, but retry/DLQ target readiness and broker evidence remain incomplete. | `retry-dlq-target-readiness` |
| Contact/segment parity | Contact relationships, provenance population, retention audit, and Segment Builder v2 are incomplete. | `contact-builder-relationship-cardinality`, `contact-provenance-import-population`, `contact-retention-deletion-audit`, `segment-builder-v2-*` |
| Automation depth | Activity locking, file movement, advanced nodes, artifact selectors, replay evidence, and script sandbox are incomplete. | `automation-activity-lock-concurrency-policy`, `automation-live-file-movement-storage-adapter`, `journey-advanced-node-handlers-contract`, `automation-artifact-selector-ux`, blocked automation items |
| QA maturity | Coverage thresholds, integration-test phase, full Playwright CI, visual smoke, target login smoke, and Compose health transcript are incomplete. | `backend-it-failsafe-testcontainers-profile`, `qa-*` |
| Release evidence | Strict production evidence and image/egress/GA evidence are absent. | release BLOCKED items |

Selection source of truth: .codex/backlog/queue.json.
