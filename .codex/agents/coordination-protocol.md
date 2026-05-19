# Coordination Protocol

## Shared Rules

- Read current implementation before proposing edits.
- Preserve unrelated dirty work.
- Keep ownership disjoint across agents.
- Do not read `.env` or secret material.
- Do not commit, push, deploy, or alter production systems unless explicitly requested and gates pass.
- Do not weaken safety controls to satisfy demos or throughput claims.

## Handoff Format

Each agent returns:

```text
Owner:
Scope:
Files inspected:
Findings:
Changes made:
Validation:
Memory updates needed:
Residual risks:
Next action:
```

Use `Changes made: none` for read-only agents.

## Checkpoint Cadence

Update checkpoints after:

- Initial decomposition.
- Any file edit batch.
- Validation start and completion.
- Blocker discovery.
- Before final response for long tasks.

## Conflict Handling

If two agents need the same file:

1. Program Manager assigns one writer.
2. Other agents switch to read-only review or adjacent tests.
3. Main thread integrates final changes.

If user sends a newer instruction, newest instruction wins.

## Blocker Handling

Agents resolve blockers locally when safe:

- Missing path: verify actual tree and update docs/commands.
- Failing test: inspect root cause and fix if inside scope.
- Missing external evidence: mark blocked; do not invent evidence.
- Missing secret: ask user only if the task explicitly requires it; otherwise use `.env.example`.

## Memory Merge

The main thread owns final memory writes. Subagents report memory deltas; main thread merges them into the correct files.
