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

Validate with route map, Compose config, Kustomize render, production overlay validator, repo artifact hygiene, release evidence self-tests, and local release gate as relevant. Keep memory compact; detailed activity belongs in audit events and checkpoints.
```
