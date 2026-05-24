# Automation Studio Activity Orchestration Plan

Date: 2026-05-20.

Local reconciliation: 2026-05-22. No new Salesforce or competitor source refresh was performed.

Parent backlog item: `automation-studio-activity-orchestration`.

Security contract: `docs/product/automation-studio-activity-security-contract.md`.

## Current Evidence

Automation Studio currently exposes activity types `SQL_QUERY`, `FILE_DROP`, `IMPORT`, `EXTRACT`, `SCRIPT`, `WEBHOOK`, `NOTIFICATION`, and `SEND_EMAIL`. `SQL_QUERY`, `FILE_DROP`, `IMPORT`, artifact-backed `EXTRACT`, `WEBHOOK`, `NOTIFICATION`, and `SEND_EMAIL` have supported local live execution, and live execution requires an active activity plus explicit `confirmLiveRun=true` and an idempotency key. Empty run requests default to dry run; data-extension extract generation and script execution remain blocked.

Existing SQL and import side effects call audience-service internal endpoints with tenant/workspace headers and an internal token. Activity configs, verification output, and run result JSON are persisted and returned to clients, so new activity families must treat those fields as non-secret, redacted control data only.

Workflow graph orchestration is a separate runtime with node/edge semantics, instance history, idempotent trigger consumption, and limited runtime-supported node types. Local Automation Studio contracts now cover validation-only dependency/order metadata, bounded run listing/detail shape, failure policy fields, run trace fields, capability UI, notification handoff, guarded webhook handoff, artifact-ID import handoff, governed send handoff, tenant/workspace-scoped file movement through a storage adapter, and Quartz-backed `WAIT_UNTIL` journey waits. Data-extension extract provider handoff, script execution, branch/split runtime aliases, target replay evidence, provider egress evidence, and production operational evidence remain future or blocked work.

## Non-Goals

- Do not add arbitrary script execution.
- Do not add external storage credentials, raw object URLs, raw auth headers, provider secrets, private keys, or tokens in activity config.
- Do not broaden send behavior or bypass campaign, suppression, warmup, rate, inbox-safety, or preflight controls.
- Do not add public internal routes without route-map, Nginx, ingress, token, and security-chain coverage.
- Do not claim Salesforce Automation Studio parity until each activity family has implementation, tests, and operational evidence.

## Safety Requirements

- Config and result JSON must reject raw secret-looking keys and redact sensitive values before persistence or response.
- File/import/extract work must use tenant/workspace-scoped service-minted artifact IDs, object ownership metadata, size/hash/content-type checks, and tenant-prefixed storage paths.
- Script work remains non-executable until signed artifact, sandbox, runtime limit, no-ambient-secret, and audit requirements are approved and tested.
- Webhook work must use `OutboundUrlGuard`, resolved-address blocking, secret refs for auth, response-size limits, timeout/retry policy, and idempotency.
- Send activities must have explicit governed handoff semantics and must not turn a journey/contact send into a broad campaign launch without an approved flag and campaign preflight proof.
- Kafka side effects should move toward outbox or after-commit publication before expanding orchestration fan-out.

## Split Work Items

| Order | Work Item | Status | Owner | Scope | Key Validation |
|---:|---|---|---|---|---|
| 1 | `automation-activity-security-design` | DONE_LOCAL | SECURITY_ENGINEER | Defines config redaction, secret-ref policy, artifact ownership, route/internal-token requirements, webhook/script/send safety contracts, and test matrix. | Codex validation, repo artifact hygiene, docs diff check. |
| 2 | `automation-activity-dependency-run-contract` | DONE_LOCAL | AUTOMATION_SERVICE_OWNER | Adds validation-only dependency/order metadata, cycle detection, bounded run listing/detail schema, failure policy, and run trace fields without new activity-family side effects. | Focused automation-service tests plus full automation-service gate. |
| 3 | `automation-activity-capability-verification-ui` | DONE_LOCAL | FRONTEND_OWNER | Shows activity capability metadata, per-type config panels, verify results, unsupported/draft-only states, row-scoped errors, dependency/failure policy, and run trace fields in Automation Studio. | Frontend lint/build and automation Playwright specs. |
| 4 | `automation-file-trigger-extract-family` | DONE_LOCAL | AUTOMATION_SERVICE_OWNER | Adds scoped automation artifact metadata, artifact-ID import handoff, validation-only file-drop/extract dry runs, redacted run summaries, and audience object-key hardening. Live file movement remains gated on storage-adapter evidence. | Automation/audience focused tests, full automation/audience gate, frontend lint/build/Playwright, route validation, artifact hygiene, Codex validation. |
| 5 | `automation-webhook-notification-family` | DONE_LOCAL | PLATFORM_SERVICE_OWNER | Adds guarded webhook platform-event handoff and terminal notification activity support with reference-based auth, idempotent live runs, platform retry/idempotency ownership, bounded/redacted webhook response persistence, and frontend authoring coverage. | Automation/platform/shared tests, frontend lint/build/Playwright, route validation, artifact hygiene, Codex validation, `git diff --check`. |
| 6 | `automation-send-activity-handoff` | DONE_LOCAL | CAMPAIGN_SERVICE_OWNER | Adds governed `SEND_EMAIL` activity and workflow-node handoff to `send.requested` with explicit launch confirmation, tenant/workspace context, deterministic idempotency, unsafe override rejection, and campaign-service send lifecycle ownership. | Focused automation, campaign, and shared Kafka tests; broader delivery/deliverability gate remains required before release claims. |
| 7 | `automation-script-activity-security-sandbox` | BLOCKED | SECURITY_ENGINEER | Keep scripts blocked until signed artifact execution, sandboxing, egress/file limits, runtime caps, audit, and approval model are designed. | Security design decision plus sandbox validation. |

## Remaining Backlog Split

These are docs/backlog descriptions only. They keep completed local contracts separate from future Automation Studio parity and target-evidence work.

| Work Item | Status | Scope | Depends On / Evidence |
|---|---|---|---|
| `automation-script-activity-security-sandbox` | BLOCKED | Design signed script artifacts, sandbox execution, no-ambient-secret runtime, egress/file limits, CPU/memory/time caps, audit, approval, and rollback behavior. Inline or unsigned scripts remain rejected. | Security architecture approval, sandbox validation, operational monitoring. |
| `automation-live-file-movement-storage-adapter` | DONE_LOCAL | Promotes file-drop and artifact-backed extract movement through a tenant/workspace-scoped MinIO storage adapter with size/hash/content-type verification, idempotent live-run gating, redacted run results, and no raw object-key input. | Target object-store integration proof, cleanup/retention drills, and provider-backed data-extension extract handoff remain required before parity or release claims. |
| `automation-target-runtime-replay-evidence` | FUTURE_EVIDENCE | Prove live automation side effects are idempotent under Kafka replay, outbox retry, partial failure, and process restart. | Target Kafka/PostgreSQL evidence, replay drills, duplicate-effect assertions. |
| `automation-activity-lock-concurrency-policy` | FUTURE | Add explicit activity/automation lock semantics, operator override policy, retry-after behavior, and audit so concurrent runs cannot duplicate side effects. | Automation-service tests, UI state coverage, operator runbook. |
| `automation-delivery-policy-snapshot-handoff` | FUTURE_CONTRACT | Align governed send activity handoff with immutable delivery-owned policy snapshots so retries and feedback reconcile to the exact policy used at execution time. | `delivery-policy-runtime-snapshot-contract`, campaign/delivery/content contract tests. |
| `automation-provider-egress-release-proof` | BLOCKED_EXTERNAL | Collect provider egress, warmup, capacity, suppression, feedback-loop, and monitoring evidence before claiming production Automation Studio send parity. | External provider and target-environment evidence. |

## Release Notes

The local orchestration-contract portion of this parent is complete for the `DONE_LOCAL` child slices above and Salesforce parity docs now record the split. Salesforce Automation Studio parity is not complete until blocked script sandboxing, live file movement, replay evidence, delivery-policy snapshots, provider egress, and production operational evidence are separately delivered.

2026-05-20 local update: `automation-file-trigger-extract-family` now has a local artifact ownership foundation. Automation artifacts are service-minted and tenant/workspace scoped; import activities require `artifactId` instead of raw object keys; file-drop and extract activities can record validation-only dry-run history without moving files; live import requires explicit confirmation and an idempotency key; audience internal imports now reject raw URLs, traversal, and unscoped object keys.

2026-05-20 local update: `automation-webhook-notification-family` now has local guarded webhook and notification execution. Automation Studio webhook activities publish bounded `automation.*` platform events to the platform webhook topic instead of accepting raw endpoints. Notification activities require terminal status policy, explicit recipient/title/message fields, and app-relative links. Platform webhook delivery keeps endpoint ownership, outbound URL guard, signatures, retries, idempotency, and bounded/redacted response persistence.

2026-05-20 local update: `automation-send-activity-handoff` now has local governed send activity support. Automation Studio `SEND_EMAIL` activities and workflow `SEND_EMAIL` nodes can publish confirmed campaign launch requests only through campaign orchestration. The handoff carries tenant/workspace context, source activity/run or workflow/node identity, deterministic idempotency, and confirmation metadata while rejecting recipient, content, sender, provider, governance-policy, and safety-control overrides.

2026-05-24 local update: `automation-live-file-movement-storage-adapter` now has a local governed storage adapter boundary. File-drop activities can copy a scoped source artifact to a scoped output artifact, and artifact-backed extracts can copy a scoped source artifact to a generated output artifact. The adapter verifies tenant/workspace object-key prefixes, size, SHA-256, and CSV-compatible content type, and run history stores compact artifact IDs plus checksum metadata only. Data-extension extract generation, cleanup drills, target object-store evidence, and production parity remain unclaimed.

2026-05-24 local update: `journey-advanced-node-handlers-contract` now has first incremental runtime expansion through a `WAIT_UNTIL` journey node. The node accepts `configuration.at` or `configuration.until` as an ISO-8601 instant, schedules resume through the existing Quartz workflow wake path, passes through when the timestamp is already due, and rejects waits beyond 10080 minutes. Journey Builder capability metadata now enables `WAIT_UNTIL` and exposes timestamp editing. Branch/split aliases, webhook/contact side-effect nodes, replay proof, and parity claims remain open.
