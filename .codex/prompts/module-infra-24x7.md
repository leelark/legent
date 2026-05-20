# Prompt: Module Infra 24x7

```text
Start a Legent infrastructure/module-level autonomous thread.

Thread ID: {{THREAD_ID}}
Module: {{MODULE}}
Owner: {{OWNER}}
Backlog item: {{BACKLOG_ITEM_ID}}
Allowed paths: {{ALLOWED_PATHS}}
Forbidden paths: {{FORBIDDEN_PATHS}}
Validation profile: {{VALIDATION_PROFILE}}
Memory targets: {{MEMORY_TARGETS}}

Read AGENTS.md, .codex/bootstrap.md, .codex/teams/module-team-registry.json, .codex/threads/thread-registry.json, .codex/backlog/queue.json, release/blocked/risk memory, route map, Nginx config, Kubernetes overlays, CI/security workflow, and ops scripts.

Register this thread, acquire exact infra/script leases, create a checkpoint, and heartbeat during validation. Do not weaken release gates, evidence requirements, network policy, image provenance, route protection, or secret handling.

Use maximum safe parallelization with up to 6 active subagents by default whenever independent infra, CI, validation, docs, or script work exists inside this module scope. Prefer near-full utilization and dynamically spawn or reassign subagents as work completes, while keeping responsibilities strictly disjoint with clear ownership and minimal overlap. Continuously rebalance tasks and reduce concurrency only when dependencies require serialization.

Validate with route map, Compose config, Kustomize render, production overlay validator, repo artifact hygiene, release evidence self-tests, and local release gate as relevant. Keep memory compact; detailed activity belongs in audit events and checkpoints. Keep working in a continuous loop and do not stop unless explicitly told to stop, or unless no safe local infra work remains and all remaining work is blocked by dependencies, external evidence, credentials, production access, or human decision.
```
