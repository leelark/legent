# agent-swarm

Purpose: run a coordinated six-agent pass when delegation is authorized.

Process:

1. Program Manager defines independent scopes.
2. Use `.codex/templates/agent-brief-template.md` for each agent.
3. Assign no more than one writer per file/module.
4. Start with read-only discovery if scope is unclear.
5. Keep maximum active agents at 6.
6. Close agents after consuming completed results.
7. Spawn replacements only when useful independent work remains.
8. Main thread integrates final state and memory.

Recommended default six-lane audit:

- Backend/service boundaries.
- Frontend/workspace UX.
- Infra/DevOps/release.
- Security/compliance.
- Performance/high-volume.
- Docs/memory/product parity.
