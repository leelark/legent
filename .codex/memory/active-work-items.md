# Active Work Items

Fresh baseline date: 2026-05-20.

## Live State

Current overall coordination work:

None. Overall thread `overall-20260520T181712Z` is safe-stopped at user request. `tracking-ingestion-batch-consumer-readiness` is locally implemented and validated, but moved to `BLOCKED` pending Docker/PostgreSQL and ClickHouse runtime evidence. Lease `overall-20260520T181712Z-tracking-ingestion-batch-consumer-readiness-20260520T214747Z` is released; all helper agents are closed.

Active overall work:

| Work Item | Owner | Scope |
|---|---|---|
| _None_ | _N/A_ | No active work remains after safe stop. |

Active implementation workers:

| Agent | Lane | Scope |
|---|---|---|
| _None_ | _N/A_ | All tracking implementation and review agents are closed. |

Active module threads:

| Thread | Owner | Scope |
|---|---|---|
| _None_ | _N/A_ | No active overall or module thread is currently running. |

READY planned assignments:

| Work Item | Owner | Module |
|---|---|---|
| _None currently_ | _N/A_ | No READY item is currently promoted outside the active tracking slice. |

Safe-stopped module threads:

| Thread | Owner | Last Work | Resume From |
|---|---|---|---|
| `frontend-20260520T113115Z` | FRONTEND_OWNER | Completed `campaign-budget-frequency-mode-contract`, `automation-mode-contract`, and `template-studio-mode-contract`; source validation passed and thread was safe-stopped at user request. | Resume only if a new frontend module request arrives; start by reading `.codex/checkpoints/20260520T124500Z-template-studio-mode-contract.json` and this handoff before selecting any new frontend work. |
| `audience-service-20260520T121322Z` | AUDIENCE_SERVICE_OWNER | Completed `predictive-segments-governance` and the no-schema backend slice `contact-data-designer-preview-governance`; source validation passed and thread was safe-stopped at user request. | Resume `contact-data-designer-governance` from additive provenance/classification/audit tables, controller/API validation, frontend relationship-designer controls, or the smaller internal endpoint security-chain test slice. |
| `foundation-service-20260520T100717Z` | FOUNDATION_SERVICE_OWNER | Completed admin settings context mismatch, config by-ID scope, differentiation upsert exact-match, public contact admin restriction, config create/upsert mismatch, tenant get self-scope, and the overall-thread foundation scoped hardening follow-ups. | Resume only for a new foundation-specific request; remaining policy/schema blockers are tenant lifecycle policy and tenant-scoped public contact inbox. |
| `overall-20260520T133124Z` | PROGRAM_MANAGER_AGENT | Completed the ONE_OVERALL_TEAM run through `email-governance-policy-objects`; all leases released and user requested safe stop. | Resume from `.codex/checkpoints/20260520T164135Z-email-governance-policy-objects.json` and `.codex/threads/overall-20260520T133124Z-handoff.md`. Highest related candidates: `automation-send-activity-handoff` or delivery/message policy snapshot work. |
| `overall-20260520T181712Z` | PROGRAM_MANAGER_AGENT | Completed local tracking ingestion batch-consumer hardening and safe-stopped with the work item blocked on Docker/PostgreSQL and ClickHouse runtime evidence. | Resume from `.codex/checkpoints/20260520T214747Z-tracking-ingestion-batch-consumer-readiness.json` and `.codex/threads/overall-20260520T181712Z-handoff.md`; do not mark DONE until ClickHouse dedupe/reconciliation evidence is collected. |
| `frontend-20260520T100638Z` | FRONTEND_OWNER | Completed `mode-aware-workflow-contract`: typed BASIC/ADVANCED mode metadata, render-time Settings navigation filtering, Admin role-gate separation, and campaign Experiment Engine render/payload gating. | Archived before `frontend-20260520T113115Z`; budget/frequency follow-up completed in `.codex/checkpoints/20260520T113830Z-campaign-budget-frequency-mode-contract.json`. |
| `overall-24x7` | PROGRAM_MANAGER_AGENT | Completed `frequency-optimization-deterministic-policy-contract`; later stale after safe stop. | Archived and superseded by `multi-module-coordinator-20260520T113233Z`. |

Coordinator cleanup:

The duplicate module scouts started by `overall-24x7` for deliverability, content, audience, automation, tracking, and `frontend-20260520T100626Z` were closed or paused after detecting active frontend/foundation module threads. This prevents duplicate module work and shared path overlap.

Source of truth:
- Live thread/team state: `.codex/threads/thread-registry.json`
- Live assignments: `.codex/state/team-state.json`
- Work queue and history: `.codex/backlog/queue.json`
- Detailed activity trail: `.codex/audit/events/YYYY-MM-DD.jsonl`
- Session checkpoints: `.codex/checkpoints/*.json`
- Narrative evidence: `.codex/reports/`

## Next Action

No active next action remains in this thread. Resume `tracking-ingestion-batch-consumer-readiness` only when Docker/PostgreSQL and ClickHouse runtime evidence can be collected; the item is blocked until raw-events dedupe/reconciliation for ambiguous or partial batch writes is proven. Pending-scan candidates remain rendered production image evidence inventory and frontend data-extension governance drawer.

Foundation module status:
- `foundation-service-20260520T100717Z` completed `admin-settings-context-mismatch-fail-closed`; focused and full foundation tests passed.
- `foundation-service-20260520T100717Z` completed `config-by-id-tenant-scope`; focused `ConfigServiceTest`, full foundation-service tests, and touched-file diff check passed.
- `foundation-service-20260520T100717Z` completed `differentiation-upsert-workspace-exact-match`; focused `DifferentiationPlatformServiceTest`, full foundation-service tests, and touched-file diff check passed.
- `foundation-service-20260520T100717Z` completed `public-contact-admin-platform-admin-only`; focused contact tests, full foundation-service tests, and touched-file diff check passed.
- `foundation-service-20260520T100717Z` completed `config-create-upsert-context-mismatch-fail-closed`; focused `ConfigServiceTest`, full foundation-service tests, and touched-file diff check passed.
- `foundation-service-20260520T100717Z` completed `tenant-get-self-scope`; focused `TenantServiceTest`, full foundation-service tests, and touched-file diff check passed.
- Safe stop requested by user after foundation reassessment completed. Resume from `.codex/checkpoints/20260520T131300Z-foundation-service-safe-stop.json` and `.codex/threads/foundation-service-20260520T100717Z-handoff.md`.
- Remaining foundation blockers: tenant lifecycle list/get-by-slug/mutation controls need a platform-admin versus self-tenant policy decision; tenant/workspace public contact inbox needs product/schema design. Config version-history scoping is fixed locally.
- Compliance privacy-request workspace scoping, nullable permission-list JSON defaulting, differentiation evaluate exact workspace matching, core-platform workspace consistency/access-grant guards, permission-group tenant mismatch guard, and role/access read scoping are fixed locally.

Audience module status:
- `audience-service-20260520T121322Z` completed `predictive-segments-governance`; focused predictive tests, full audience-service tests, Codex validation, repo artifact hygiene, and scoped diff check passed.
- `audience-service-20260520T121322Z` completed `contact-data-designer-preview-governance`; focused data-extension/segment tests, full audience-service tests, Codex validation, repo artifact hygiene, and scoped diff check passed.
- Safe stop requested by user after completion. Resume from `.codex/checkpoints/20260520T125000Z-contact-data-designer-preview-governance.json` and `.codex/threads/audience-service-20260520T121322Z-handoff.md`.

## Memory Budget Rule

Keep this file short. Do not paste completed-work history or command logs here. Store durable completions in `.codex/backlog/queue.json`, reports, and the owning memory file.
