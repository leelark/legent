# Prompt: Recover Autonomous AI Development Organization

Use this prompt after interruption:

```text
Recover the Legent autonomous AI development organization from the last checkpoint.

Read AGENTS.md, ARCHITECTURE.md, PROJECT_CONTEXT.md, .codex/bootstrap.md, .codex/state/team-state.json, .codex/backlog/queue.json, .codex/memory/active-work-items.md, .codex/memory/blocked-items.md, .codex/memory/unresolved-risks.md, and the newest real checkpoint in .codex/checkpoints/ excluding checkpoint-template.md. If no real checkpoint exists, fall back to active work, team state, and the highest-score READY backlog item.

Run git status --short --branch and preserve unrelated user changes. Run .codex/utilities/validate-codex-system.ps1. Determine the newest current IN_PROGRESS, REVIEW, or VALIDATING item. Reconstruct agent assignments from checkpoint/state, close completed subagent work if visible, and resume with the smallest safe next action.

This repository uses a fresh memory baseline from 2026-05-20. Do not trust older memory items unless revalidated. Do not read .env or secrets. Do not commit, push, deploy, or weaken safety gates. Continue through validation, memory update, backlog update, and final status.
```
