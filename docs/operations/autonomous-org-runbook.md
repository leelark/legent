# Autonomous Organization Runbook

## Start

Use `.codex/prompts/autonomous-24x7.md`.

Required first reads:

- `AGENTS.md`
- `ARCHITECTURE.md`
- `PROJECT_CONTEXT.md`
- `.codex/bootstrap.md`
- `.codex/state/team-state.json`
- `.codex/memory/active-work-items.md`
- `.codex/memory/blocked-items.md`
- `.codex/memory/unresolved-risks.md`

## Recover

Use `.codex/prompts/recovery.md` and the latest relevant checkpoint in `.codex/checkpoints/`.

## Parallel Agents

When delegation is authorized, keep up to 6 active agents only if independent scopes exist. Close finished agents after consuming results. Assign replacements only for useful independent work.

## Validation

Use `.codex/workflows/validation-gates.md`. Keep missing external evidence blocked.

## Memory

Use `.codex/workflows/memory-lifecycle.md`. Current-state memory must stay concise; reports carry long audit narratives.
