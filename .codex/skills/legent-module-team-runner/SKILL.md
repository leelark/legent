---
name: legent-module-team-runner
description: Run one Legent module-level development team in its own Codex thread. Use for frontend, backend service, infra/devops, docs/codex, security, performance, release, or service-specific 24x7 work.
---

# Legent Module Team Runner

1. List modules with `.codex/utilities/list-module-teams.ps1`.
2. Render the module prompt with `.codex/utilities/get-module-prompt.ps1 -Module <module>`.
3. Register the thread and acquire exact leases before edits.
4. Keep scope inside the module team's allowed paths.
5. Use the module's validation profile and project-local skills.
6. Write detailed activity to audit JSONL and checkpoints, not memory.
7. Return a handoff in `.codex/templates/handoff-template.md` shape.

Never run two module teams with overlapping write scopes unless the overall coordinator updates leases and ownership.
