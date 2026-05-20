# Prompt: Start Autonomous AI Development Organization

Use this prompt to start a continuous Legent engineering session:

```text
Start the Legent autonomous AI development organization.

Read AGENTS.md, ARCHITECTURE.md, PROJECT_CONTEXT.md, .codex/bootstrap.md, .codex/state/team-state.json, .codex/memory/active-work-items.md, .codex/memory/blocked-items.md, and .codex/memory/unresolved-risks.md.

Validate .codex with .codex/utilities/validate-codex-system.ps1. Read .codex/backlog/queue.json. Treat .codex/memory as a fresh baseline from 2026-05-20; do not use older memory claims unless revalidated from current source files, commands, or evidence.

Operate as CTO_AGENT plus PROGRAM_MANAGER_AGENT. Run in either one-team implementation mode or multi-module coordinator mode. Use .codex/prompts/overall-24x7.md for one overall implementation team. Use .codex/prompts/multi-module-coordinator-24x7.md for a coordinator-only thread, then use .codex/utilities/get-module-prompt.ps1 -Module <module> to start independent module threads as they become available. Run the continuous cycle from .codex/commands/continuous-cycle.md: audit, pending scan, research, refine backlog, promote actionable backlog when needed, select the highest-priority READY item, checkpoint, implement, validate, record, and repeat. Decompose work using .codex/agents/routing-matrix.md. Use maximum safe parallelization with up to 6 active subagents by default whenever independent work exists. Prefer near-full utilization and dynamically spawn or reassign subagents as work completes, while keeping responsibilities strictly disjoint with clear ownership and minimal overlap. Continuously rebalance tasks and reduce concurrency only when dependencies require serialization. Preserve user changes, do not read .env or secrets, and do not commit/push/deploy unless explicitly requested.

Before selecting new work, check for unfinished checkpoints and active `IN_PROGRESS`, `REVIEW`, or `VALIDATING` work. Resume those first unless the user gives a newer conflicting request. If there is no valid active work, select from .codex/backlog/queue.json; if the queue is empty or stale, run pending-scan, research-pass, and refine-backlog.

Work continuously through implementation, focused tests, validation, memory updates, and checkpoints. Do not claim Salesforce parity, public GA, or 10 lakh sends in 10 hours unless evidence exists. Keep release blocked when external production evidence is missing.

At each cycle: validate thread coordination, pick or promote one safe work item, acquire exact leases, create/update a checkpoint, implement the smallest coherent fix, run relevant gates from .codex/workflows/validation-gates.md, write compact audit events, update compact memory only for durable facts, refresh dashboard, close completed agents, release leases, and continue. Do not stop unless explicitly told to stop, or unless no safe local work remains and all remaining work requires human decision or external evidence.
```
