# Automation Studio Activity Security Contract

Date: 2026-05-20.

Backlog item: `automation-activity-security-design`.

## Source Evidence

- `AutomationActivity.inputConfig`, `outputConfig`, and `verificationJson` are persisted as JSON and returned by activity responses.
- `AutomationActivityRun.resultJson` and `errorMessage` are persisted and returned by run-history responses.
- `AutomationStudioService` currently supports live execution for `SQL_QUERY`, `FILE_DROP`, `IMPORT`, artifact-backed `EXTRACT`, `WEBHOOK`, `NOTIFICATION`, and `SEND_EMAIL`; data-extension extract generation and script execution remain blocked.
- `AudienceDataExtensionClient` calls audience-service internal SQL/import routes with tenant/workspace headers and an internal service credential.
- `OutboundUrlGuard` already blocks non-public resolved addresses, localhost-style hosts, reserved IPv4/IPv6 ranges, user-info URLs, and non-HTTPS URLs when required.
- `SendEmailNodeHandler` and Automation Studio `SEND_EMAIL` activities publish confirmed campaign send requests with `campaignId`, workspace/environment context, source workflow/node or activity/run identity, deterministic idempotency, and campaign-service lifecycle ownership.

## Required Invariants

1. Activity config, verification output, run results, and errors are client-visible control data. They must not contain raw credentials, private keys, provider access material, auth headers, cookies, connection strings, customer payloads, or full remote responses.
2. Future create, update, verify, run, and list paths must share one sanitization/redaction policy before JSON is persisted or returned.
3. Any auth material in activity config must be represented by a tenant/workspace-scoped secret reference, not a raw value.
4. Tenant, workspace, environment, actor, request ID, idempotency key, and source activity/run IDs must be preserved across side-effect calls and event publications.
5. Dry run remains the default. Live side effects require explicit confirmation, active status, successful verification, and activity-family-specific safety checks.
6. Unsupported families remain fail-closed until the owning implementation slice adds tests and operational evidence.
7. New internal routes must be service-token gated, tenant/workspace-header validated, denied at public edge, covered by route-map/Nginx/ingress validation, and tested for public 404 or denial behavior.

## Redaction Contract

Add a shared automation activity config sanitizer before adding executable families beyond SQL/import.

Required behavior:

- Reject raw sensitive key names at any nesting level, including `password`, `secret`, `token`, `apiKey`, `accessKey`, `privateKey`, `authorization`, `cookie`, `smtpPassword`, and close variants.
- Allow reference fields only when they are explicitly named and shaped as references, for example `credentialRef`, `webhookAuthRef`, `storageConnectionRef`, or `scriptArtifactRef`.
- Redact result and error maps before persistence and response. Persist compact IDs, status, counts, timestamps, checksums, trace IDs, and policy decisions rather than raw payloads.
- Cap stored JSON depth, key count, string length, array length, and total serialized bytes for config, verification output, and run results.
- Normalize error messages to implementation-owned messages. Do not persist upstream exception messages that can contain URL, header, SQL, object-key, or provider response details.

Minimum tests:

- Create/update rejects raw sensitive keys in nested input and output config.
- Verification output redacts sensitive-looking normalized config.
- Run result redacts upstream response maps before `resultJson` persistence.
- Error handling stores normalized messages for failed side effects.
- Listing activities and runs returns the same redacted shape as persistence.

## Artifact Ownership Contract

File, import, extract, and file-transfer work must move from raw object-key thinking to service-owned artifacts.

Required fields:

- `artifactId`: service-minted opaque ID.
- `tenantId`, `workspaceId`, and optional `environmentId`.
- `objectKey`: service-generated tenant/workspace-prefixed storage key, never supplied raw by the client.
- `contentType`, `sizeBytes`, `sha256`, `createdBy`, `createdAt`, `expiresAt`, and retention policy.
- `sourceKind`: upload, inbox, provider export, generated extract, or approved connector.

Rules:

- Deny raw external URLs, path traversal, absolute paths, backslashes, unscoped object keys, and client-supplied storage credentials.
- Resolve artifacts by current tenant/workspace before any dry-run or live action.
- Validate content type, extension, size, checksum, and retention before import or extract execution.
- Record artifact metadata in run results, not raw file names or signed URLs.
- Keep object-store credentials outside activity config and outside run history.

## Activity Family Requirements

| Family | Required Boundary |
|---|---|
| SQL query | Keep SELECT-only/comment-free validation, row caps, dry-run support, target data extension ownership checks, and bounded result metadata. Do not store query result rows in run history. |
| Import | Require scoped artifact IDs, target ownership checks, mapping validation, explicit live confirmation, idempotency, and import job ID/status only in run results. |
| File drop and extract | Live file movement must use scoped source and output artifacts, checksums, content-type and size checks, retention policy, idempotency, activity locks, and storage adapter tests. Data-extension extract generation remains blocked until a provider handoff is implemented and tested. |
| Webhook | Automation Studio must publish platform-owned `automation.*` events only. Platform webhook config owns endpoints, `OutboundUrlGuard.requirePublicHttpsUri`, resolved-address blocking, reference-based auth, timeout/retry policy, idempotency keys, response-size limits, and redacted terminal response storage. |
| Notification | Notify only terminal states or configured thresholds, require explicit recipient/title/message/severity/link fields, keep links app-relative, rate-limit duplicate notifications through idempotent platform events, redact PII, and record notification failure without masking the activity failure. |
| Send | Use campaign-service ownership, explicit governed launch semantics, campaign/workspace ownership checks, send policy/preflight proof, suppression, warmup, rate, provider health, inbox-safety checks, and idempotency. Do not turn a contact-level journey action into a broad campaign launch without an explicit governed flag. |
| Script | Remain blocked. Future support requires signed artifacts, sandboxing, no ambient credentials, no inline scripts, runtime/resource limits, egress/file restrictions, audit, and operations approval. |

## Dependency And Run-History Requirements

- Dependency order must be explicit, tenant/workspace scoped, cycle-checked, and stable across retries.
- Upstream failure behavior must be one of: stop all, skip dependents, continue independent branches, or require manual resume.
- Run history must be pageable and bounded. Default unbounded listing is not acceptable for high-volume or long-lived tenants.
- Run detail should include activity ID, dependency parent IDs, status, dry/live mode, trigger source, trace ID, rows/counts, started/completed timestamps, normalized error code, and redacted result summary.
- Retry and resume must reuse stable idempotency keys and must not repeat successful side effects.

## Event And Transaction Requirements

- New Kafka side effects should use an outbox or after-commit publication pattern when DB state and event publication must remain consistent.
- Event envelopes must include tenant/workspace, source activity/run IDs, trace/correlation ID, idempotency key, and schema version.
- Consumers must claim idempotency before side effects and mark failure without hiding the original activity failure.

## Internal Route Checklist

Every new service-to-service route used by Automation Studio must have:

- Controller-level internal service credential validation.
- Tenant/workspace header validation and fail-closed missing context behavior.
- Nginx public-edge denial and Kubernetes ingress denial when exposed through the edge.
- Route-map entry or validation coverage that proves no public drift.
- Focused tests for missing credential, invalid credential, missing tenant/workspace, cross-workspace resource, and public-edge denial.

## Implementation Order

1. Add sanitizer/redaction utilities and tests.
2. Add dependency/run-history contract and bounded listing. DONE_LOCAL in `automation-activity-dependency-run-contract`.
3. Add capability UI so unsupported families are visible as unavailable, not implied. DONE_LOCAL in `automation-activity-capability-verification-ui`.
4. Add file/import/extract artifact ownership. DONE_LOCAL in `automation-file-trigger-extract-family`.
5. Add webhook/notification execution. DONE_LOCAL in `automation-webhook-notification-family`.
6. Add governed send handoff. DONE_LOCAL in `automation-send-activity-handoff`.
7. Revisit script only after sandbox approval and operational evidence.

## Residual Risks

- Local import execution now resolves `inputConfig.artifactId` to automation-owned artifact metadata and passes the service-generated object key only to the internal audience-service handoff. File-drop and artifact-backed extract execution now use a local storage adapter that verifies scoped object keys, size, SHA-256, and CSV-compatible content type without persisting raw object keys. Target object-store drills, cleanup/retention proof, and data-extension extract provider handoff remain unclaimed.
- Local webhook/notification execution now uses platform event handoff, explicit live confirmation, idempotency keys, terminal notification policy, and bounded/redacted platform webhook response persistence. Target platform migration, Kafka replay, provider egress, and real endpoint behavior still require environment evidence before release claims.
- Local send activity execution now uses campaign-service handoff only, requires explicit live confirmation and idempotency, rejects unsafe send overrides, and enforces `send.requested` confirmation/idempotency contracts. Delivery-owned immutable policy snapshots, target Kafka replay, provider capacity, deliverability evidence, and live send-path proof remain required before release or parity claims.
- Current workflow send publication happens inside the workflow transaction. Broader orchestration fan-out should be paired with outbox or after-commit publication.
- Run-history listing is locally bounded/pageable, but retention and high-volume query behavior still need target evidence before high-volume automation claims.
