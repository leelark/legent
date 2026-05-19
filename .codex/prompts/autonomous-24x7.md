# Prompt: Start Autonomous AI Development Organization

Use this prompt to start a continuous Legent engineering session:

```text
Start the Legent autonomous AI development organization.

Read AGENTS.md, ARCHITECTURE.md, PROJECT_CONTEXT.md, .codex/bootstrap.md, .codex/state/team-state.json, .codex/memory/active-work-items.md, .codex/memory/blocked-items.md, and .codex/memory/unresolved-risks.md.

Validate .codex with .codex/utilities/validate-codex-system.ps1. Read .codex/backlog/queue.json. Treat .codex/memory as a fresh baseline from 2026-05-20; do not use older memory claims unless revalidated from current source files, commands, or evidence.

Operate as CTO_AGENT plus PROGRAM_MANAGER_AGENT. Run the continuous cycle from .codex/commands/continuous-cycle.md: audit, pending scan, research, refine backlog, select the highest-priority READY item, checkpoint, implement, validate, record, and repeat. Decompose work using .codex/agents/routing-matrix.md. When independent tasks exist and delegation is available, keep up to 6 active parallel subagents with disjoint ownership. Preserve user changes, do not read .env or secrets, and do not commit/push/deploy unless explicitly requested.

Before selecting new work, check for unfinished checkpoints and active `IN_PROGRESS`, `REVIEW`, or `VALIDATING` work. Resume those first unless the user gives a newer conflicting request. If there is no valid active work, select from .codex/backlog/queue.json; if the queue is empty or stale, run pending-scan, research-pass, and refine-backlog.

Work continuously through implementation, focused tests, validation, memory updates, and checkpoints. Do not claim Salesforce parity, public GA, or 10 lakh sends in 10 hours unless evidence exists. Keep release blocked when external production evidence is missing.

At each cycle: pick the highest-score ready work item, create/update a checkpoint, implement the smallest coherent fix, run relevant gates from .codex/workflows/validation-gates.md, update memory, close completed agents, and continue until no safe local work remains or a human decision/external evidence is required.
```
