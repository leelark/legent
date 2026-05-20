# Prompt: Module Backend Service 24x7

```text
Start a Legent module-level autonomous thread.

Thread ID: {{THREAD_ID}}
Module: {{MODULE}}
Owner: {{OWNER}}
Backlog item: {{BACKLOG_ITEM_ID}}
Allowed paths: {{ALLOWED_PATHS}}
Forbidden paths: {{FORBIDDEN_PATHS}}
Validation profile: {{VALIDATION_PROFILE}}
Memory targets: {{MEMORY_TARGETS}}

Read AGENTS.md, ARCHITECTURE.md, PROJECT_CONTEXT.md, .codex/bootstrap.md, .codex/teams/module-team-registry.json, .codex/threads/thread-registry.json, .codex/backlog/queue.json, .codex/memory/repo-map.md, .codex/memory/service-dependencies.md, .codex/memory/active-work-items.md, .codex/memory/blocked-items.md, .codex/memory/unresolved-risks.md, and the target module implementation.

Register this thread:
powershell -ExecutionPolicy Bypass -File .codex\utilities\register-thread.ps1 -ThreadId "{{THREAD_ID}}" -ThreadRole MODULE -Module "{{MODULE}}" -BacklogItemId "{{BACKLOG_ITEM_ID}}"

Before edits, acquire a lease for exact module paths:
powershell -ExecutionPolicy Bypass -File .codex\utilities\acquire-lease.ps1 -ThreadId "{{THREAD_ID}}" -WorkItemId "<work-item-id>" -FilesInScope <exact paths>

Create a checkpoint before edits. Work only inside allowed paths unless the overall coordinator explicitly updates the lease and scope. Preserve tenant/workspace isolation, service ownership, Flyway-forward migrations, Kafka contracts, API envelopes, retry/DLQ behavior, and safety controls.

Late-join rule: this module thread may start after the coordinator. Do not treat earlier coordinator planning as a blocker. Only an active overlapping source-code lease, failed validation in this module scope, missing external evidence, credentials, production access, or explicit human decision can block implementation. If a shared .codex metadata lease is active, continue source work and defer only the metadata write/handoff until that exact lease clears.

Use maximum safe parallelization with up to 6 active subagents by default whenever independent backend work exists inside this module scope. Prefer near-full utilization and dynamically spawn or reassign subagents as work completes, while keeping responsibilities strictly disjoint with clear ownership and minimal overlap. Continuously rebalance tasks and reduce concurrency only when dependencies require serialization.

Heartbeat at meaningful milestones:
powershell -ExecutionPolicy Bypass -File .codex\utilities\heartbeat-thread.ps1 -ThreadId "{{THREAD_ID}}" -NextAction "<next action>"

Keep memory compact. Write detailed activity to audit events and checkpoints. Return handoff using .codex/templates/handoff-template.md. Release leases only when done or blocked. Keep working in a continuous loop and do not stop unless explicitly told to stop, or unless no safe local backend work remains and all remaining work is blocked by dependencies, external evidence, credentials, production access, or human decision.
```
