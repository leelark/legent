# Active Work Items

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

## Live State

- Active implementation work: none. The 2026-05-26 overall rescan completed frontend unit coverage hardening, local release-gate backend serialization, signed internal-route identity expansion, Docker build-context hygiene, release-evidence validator timeout hardening, and the local Compose starter after the blocked-item audit found no locally resolvable external blockers.
- Active subagents: none.
- Active leases/worktrees: none after lease release closeout.
- Queue state: READY 0, BACKLOG 0, IN_PROGRESS 0, BLOCKED 16, DONE 216.
- Current source of truth: .codex/backlog/queue.json.
- Historical readiness report: .codex/reports/100-percent-readiness-backlog-2026-05-23.md. Current queue state supersedes its stale READY/BACKLOG counts.

## Next Action

2026-05-30 local live-stack E2E validation: Docker Compose stack started from `.env.example` with transient local placeholder overrides, 23-service health passed, signup/onboarding and Email, Audience, Campaigns, Automation, Tracking, Deliverability, and Admin browser navigation passed with zero captured console errors, page errors, request failures, API 4xx/5xx responses, visible alert states, application `ERROR`/`FATAL` logs, or gateway 4xx/5xx responses. `.\mvnw.cmd -DskipTests install`, route-map validation, Compose config, `npm run lint`, `npm run build:ci`, `npm run test:e2e:smoke`, and `.\mvnw.cmd test` passed. Do not claim production readiness; remaining work is target evidence, credentialed target login smoke, provider/load proof, and broader frontend coverage.

Overall team resumed on 2026-05-24. `route-edge-deny-audience-resolution-chunks`, `ai-frequency-decision-runtime`, `contact-builder-relationship-cardinality`, `delivery-rate-control-sharded-reservations`, `segment-builder-v2-execution-plan`, `ai-sto-runtime-scheduler`, `automation-live-file-movement-storage-adapter`, `contact-retention-deletion-audit`, `retry-dlq-target-readiness`, `ai-segment-workflow-generation-preview`, `journey-advanced-node-handlers-contract`, `service-to-service-identity-hardening`, `enterprise-package-export-import-contract`, `tracking-analytics-canonical-raw-query-contract`, `segment-builder-v2-governance-ui`, `automation-artifact-selector-ux`, `alertmanager-placeholder-secret-coverage`, `campaign-suppression-health-aggregate`, `delivery-inbox-safety-suppression-sync`, `service-to-service-internal-route-identity-expansion`, `frontend-automation-activity-capability-drift`, `automation-branch-split-runtime-aliases`, `qa-release-frontend-gate-parity`, and `codex-coordination-drift-refresh` are complete locally after focused/full validation. The 24x7 heartbeat automation is active, but no unblocked READY local work remains.

`product-parity-source-refresh-current` is complete locally as of 2026-05-24. Official Salesforce, Adobe, Braze, Klaviyo, and HubSpot source freshness is captured in `docs/product/competitor-research/2026-05-24-source-refresh.md`, and `docs/product/salesforce-parity-matrix.md` now treats 2026-05-24 as the current source baseline while preserving evidence-bound local-contract wording.

`qa-ci-visual-smoke-gate` is complete locally as of 2026-05-24. The existing Playwright visual shell smoke check is now required in CI and in local frontend release-gate runs, CI uploads screenshot artifacts, and `.codex/workflows/validation-gates.md` documents the screenshot review and baseline refresh policy.

Read-only audits on 2026-05-24 repopulated local READY work for internal service identity, delivery suppression signals, Automation Studio capability drift, automation branch/split aliases, release frontend gate parity, and Codex coordination drift; those local READY items are now complete. External blockers still remain for Docker runtime availability, target-environment evidence, provider/capacity proof, production promotion artifacts, or human policy decisions.

## History Location

Completed module/thread narratives were removed from this active memory file. Use .codex/backlog/queue.json doneWork, .codex/checkpoints, .codex/threads/*-handoff.md, and .codex/audit/events/ for detailed history.
