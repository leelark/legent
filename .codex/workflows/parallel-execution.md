# Parallel Execution Workflow

Use when two or more independent tasks are ready and delegation is authorized.

## Steps

1. List all independent scopes.
2. Assign at most one writer per file/module.
3. Start read-only discovery agents for broad uncertainty.
4. Start worker agents only for bounded, disjoint write sets.
5. Keep a maximum of 6 active agents.
6. Consume each completed result before closing the agent.
7. Spawn a replacement only when useful work remains.
8. Main thread integrates results and owns final validation/memory.

## Good Splits

- One backend service per agent.
- Frontend UI, API client, and Playwright tests as separate scopes.
- Infra validation scripts separate from Kubernetes manifests.
- Security review separate from performance review.
- Documentation/memory separate from implementation.

## Bad Splits

- Multiple agents editing the same file.
- Agents rewriting broad architecture docs while implementation is still unknown.
- Asking workers to fix unlocated problems.
- Letting subagents update memory independently without main-thread merge.
