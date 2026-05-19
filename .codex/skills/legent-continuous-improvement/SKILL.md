---
name: legent-continuous-improvement
description: Run Legent's continuous audit, research, backlog refinement, implementation, validation, and repeat loop. Use when the user wants the AI development team to keep finding pending work, research features, improve product capability, and continue autonomously.
---

# Legent Continuous Improvement

1. Read `.codex/commands/continuous-cycle.md`, `.codex/backlog/queue.json`, `.codex/workflows/continuous-improvement-loop.md`, and `.codex/utilities/README.md`.
2. Validate state with `.codex/utilities/validate-codex-system.ps1`.
3. Resume current valid work if present; otherwise select the highest-score `READY` item with `.codex/utilities/select-next-work.ps1`.
4. If no `READY` item exists, run pending scan, research pass, and backlog refinement.
5. Keep up to 6 independent subagents active only when parallel ownership is clear.
6. Use leases and checkpoints for parallel or long work.
7. Continue until all safe local work is done or blocked by evidence, credentials, production access, or human decision.

## Required Output

- Selected item or new queue item.
- Research/audit sources.
- Implementation and validation status.
- Queue transition.
- Memory and checkpoint updates.
