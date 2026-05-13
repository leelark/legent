# BUGFIX_SKILL.md

Use this skill whenever fixing bugs in the Legent repository.

## Bug Fixing Workflow

### 1. Reproduce Issue

- Start from the reported behavior and identify the smallest route, service, event, or UI action that reproduces it.
- Check whether the bug is frontend-only, backend-only, gateway route related, database related, Kafka/event related, or cross-service.
- Prefer an automated reproduction: unit test, integration test, Playwright test, API test, or focused script.
- Capture tenant, workspace, environment, request ID, correlation ID, event ID, job ID, batch ID, campaign ID, message ID, or subscriber ID when relevant.

### 2. Identify Root Cause

- Trace the actual implementation before editing.
- For frontend bugs, inspect route file, component state, API client behavior, auth/session hydration, middleware redirects, and workspace context.
- For backend bugs, inspect controller, DTO validation, service method, repository query, migration, security filter, tenant context, and exception handling.
- For Kafka bugs, inspect publisher, event envelope, topic, partition key, listener group, idempotency, retry/DLQ behavior, and consumer exception handling.
- For send-pipeline bugs, trace campaign launch, audience resolution, batching, render, delivery, tracking, and feedback reconciliation.

### 3. Analyze Impacted Modules

- Identify direct and indirect callers.
- Check whether data crosses tenant/workspace boundaries.
- Check whether route ownership changes require both `config/gateway/route-map.json` and `config/nginx/nginx.conf`.
- Check whether schema changes require a new Flyway migration.
- Check whether event payload changes require producer and consumer updates.
- Check whether frontend route changes affect middleware redirects or `/app` compatibility re-exports.

### 4. Implement Minimal Clean Fix

- Fix the root cause with the smallest maintainable change.
- Prefer existing local helpers, shared modules, DTOs, API clients, stores, and components.
- Preserve idempotency, tenant isolation, auth, suppression, warmup, and rate-control behavior.
- Do not weaken deliverability safety to make a send succeed.
- Do not turn real failures into silent success.

### 5. Prevent Regression

- Add or update a focused test that fails before the fix and passes after.
- For UI bugs, add Playwright coverage when user-visible routing/layout behavior is involved.
- For security bugs, test both allowed and denied cases.
- For event bugs, test envelope metadata, topic behavior, idempotency, and failure path where possible.

### 6. Add Tests

- Unit tests for pure logic, validation, mapping, policy decisions, and edge cases.
- Integration tests for database queries, migrations, Kafka consumers, service interactions, and security filters.
- E2E tests for critical user journeys in the workspace.
- Load/failure tests for send, import, tracking, provider retry, and webhook retry paths when the bug touches throughput or resilience.

### 7. Validate Performance Impact

- Check whether the fix adds per-message, per-recipient, per-event, or per-render work.
- Avoid loading unbounded collections into memory.
- Avoid one large Kafka message where chunks are required.
- Avoid new hot database rows in rate-control, warmup, or tracking paths.
- Validate query plans or indexes when changing high-volume queries.

## Rules

- Never patch blindly.
- Never hide errors.
- Never suppress exceptions without a documented reason and alternate alert/retry path.
- Fix root cause, not symptoms.
- Prefer maintainable fixes.
- Preserve tenant and workspace isolation.
- Preserve idempotency in campaign, delivery, tracking, automation, import, and webhook flows.
- Do not edit old Flyway migrations. Add new migrations.
- Do not read or expose `.env` secrets.
- Do not introduce duplicate code when a shared helper or local component already exists.
- Do not claim inbox guarantee or bypass warmup/suppression/rate control.

## Validation Checklist

- Reproduction exists or manual reproduction is documented.
- Root cause is explained in code review notes or commit message.
- Impacted modules were inspected.
- Fix is scoped.
- Tests cover success and failure behavior.
- Relevant commands ran successfully.
- No unrelated dirty worktree files were changed.
- No secrets were printed or committed.
