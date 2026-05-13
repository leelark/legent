# REFACTORING_SKILL.md

Use this skill when modernizing, simplifying, or redesigning code in the Legent repository.

Backward compatibility is not required when the user asks for a better architecture. However, the final result must be production-ready: no compile issues, no runtime issues, no broken functionality, no hidden data-loss path, and no weakened security or deliverability controls.

## Refactoring Goals

- Remove duplicate logic.
- Improve naming.
- Break large files into modules.
- Improve readability.
- Improve scalability.
- Improve maintainability.
- Improve UI/UX quality.
- Improve developer experience.
- Optimize performance.
- Modernize architecture.

## Repository-Specific Priorities

### Frontend

- Split large route files and consoles into domain components, hooks, and service modules.
- Keep the authenticated workspace practical and dense. Avoid marketing-page composition in operational tools.
- Reuse the existing API client, auth/session hydration, workspace layout, shell, sidebar, design tokens, and stores.
- Keep `/app` compatibility exports thin until route migration is intentionally removed.
- Verify responsive layout and text overflow for desktop and mobile.

### Backend

- Break orchestration-heavy services into clear collaborators.
- Keep controller, validation, orchestration, persistence, policy, integration, and event publication responsibilities separate.
- Move only stable cross-service behavior to `shared/*`.
- Keep service database ownership intact.
- Add explicit DTO/event contracts when crossing service boundaries.
- Replace catch-and-log listener behavior with retry/DLQ-aware error handling.

### Send Pipeline

- Redesign million-recipient flows around chunks, cursors, snapshots, idempotent boundaries, and backpressure.
- Do not publish full audience lists in one event.
- Do not partition high-volume events only by tenant.
- Separate audience resolution, content rendering, provider reservation, delivery execution, feedback, and analytics pressure.
- Preserve suppression, unsubscribe, inbox safety, warmup, provider health, and rate control.

### Data And Migrations

- Add new Flyway migrations for schema changes.
- Partition or index high-volume tables deliberately.
- Use batch APIs for imports, sends, tracking, and analytics where correctness allows.
- Do not introduce cross-service database reads.

### Security

- Narrow public routes.
- Narrow Kafka trusted packages.
- Keep auth cookie, tenant filter, origin guard, and SCIM token checks intact.
- Do not weaken content sanitization, signed tracking URLs, or outbound URL guard.
- Do not read or write real secret files.

## Refactoring Workflow

1. Map current behavior and call graph.
2. Identify invariants that must survive: tenant isolation, idempotency, auth, suppression, warmup, rate control, event contracts, API responses.
3. Decide the smallest useful boundary for the refactor.
4. Add characterization tests when behavior is unclear.
5. Refactor in steps that keep the project buildable.
6. Remove duplicate/dead code after replacement is verified.
7. Run targeted tests, then broader tests when shared behavior changed.
8. Update docs when architecture, commands, environment, routes, or workflows change.

## Refactoring Rules

- Do not refactor unrelated code just because it is nearby.
- Do not move code into `shared/*` unless multiple services genuinely need the same stable primitive.
- Do not add abstraction that only wraps one call site without reducing complexity.
- Prefer explicit domain names over generic helper names.
- Prefer typed DTOs and events over maps for stable contracts.
- Prefer streaming and chunked processing over unbounded lists.
- Prefer batch operations and idempotent checkpoints over per-item orchestration loops for high-volume flows.
- Prefer user-visible operational states over silent background failure.
- Delete replaced code only after confirming no live route, test, event consumer, or compatibility export needs it.
- When removing backward compatibility, remove old routes and tests intentionally and update docs.

## Production-Ready Definition

- Backend compiles.
- Frontend builds.
- Relevant tests pass.
- Database migrations apply from a clean database.
- Runtime configuration is explicit and validates required secrets.
- API routes are owned by exactly one service.
- Event contracts have tests or documented compatibility plan.
- No major workflow is left half-migrated.
- Observability covers new failure modes.
- Performance hot paths avoid unbounded memory, hot partitions, and hot rows.
