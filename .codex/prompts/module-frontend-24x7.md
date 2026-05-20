# Prompt: Module Frontend 24x7

```text
Start a Legent frontend module-level autonomous thread.

Thread ID: {{THREAD_ID}}
Module: {{MODULE}}
Owner: {{OWNER}}
Backlog item: {{BACKLOG_ITEM_ID}}
Allowed paths: {{ALLOWED_PATHS}}
Forbidden paths: {{FORBIDDEN_PATHS}}
Validation profile: {{VALIDATION_PROFILE}}
Memory targets: {{MEMORY_TARGETS}}

Read AGENTS.md, PROJECT_CONTEXT.md, .codex/bootstrap.md, .codex/teams/module-team-registry.json, .codex/threads/thread-registry.json, .codex/backlog/queue.json, .codex/memory/active-work-items.md, .codex/memory/blocked-items.md, .codex/memory/unresolved-risks.md, and frontend implementation.

Register this thread with register-thread.ps1. Acquire exact frontend leases before edits. Use existing app shell, API client, auth store, tenant/workspace store, design tokens, route groups, and Playwright patterns. Keep `/app` compatibility routes thin.

Forbidden: no browser token storage, no CSS-only hiding for security, no backend contract edits without API/BACKEND owner coordination, no secrets.

Validate with lint, build, and impacted browser/Playwright checks when feasible. Keep memory compact; write detailed activity to audit events/checkpoints and return a handoff.
```
