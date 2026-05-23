# Successful Fixes

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Detailed completed-fix history was compacted out of current-state memory. The source of truth for completed work is .codex/backlog/queue.json doneWork, with recovery detail in .codex/checkpoints and audit detail in .codex/audit/events.

## Current Durable Summary

- 2026-05-20 to 2026-05-23: 188 local work items are recorded as DONE in the queue after `audience-resolution-metadata-only-chunks` closeout.
- Completed themes include tenant/workspace fail-closed guards, route/ingress validation, bounded list/read paths, audience metadata-only resolved-chunk Kafka references backed by audience-owned durable chunk rows, outbox decoupling with terminal-row retention cleanup, tracking analytics dedupe/windowing, immutable send-governance policy snapshots before delivery dispatch, Admin UI send-governance policy management with campaign selection by named version, AI provider contract/metering ledgers with no-provider-invocation guarantees, reviewed AI draft apply UX using hash/reference evidence only, public/admin AI and deliverability claim-boundary copy, Segment Builder v2 docs-only taxonomy, Automation Studio live-run lock/override controls, import-time subscriber/data-extension provenance population, audience CSV parser/preview and modal/toast workflow polish, frontend auth/context hardening, Kafka topic drift coverage, release validator hardening, Deployment Manager local-vs-strict evidence ledger UI/docs with validator mapping, backend Failsafe integration-test phase with Testcontainers routing out of the unit gate, conservative backend/frontend coverage gates with JaCoCo/Vitest reporting, full Chromium Playwright CI/manual gate, and Campaign Wizard provider/domain selectors with inline readiness gates.
- These completions are local evidence only unless paired with target runtime, provider, release, load, restore, monitoring, and CI/security artifacts.

When adding future entries here, record only durable summaries. Put long validation logs in queue/checkpoints/audit reports.
