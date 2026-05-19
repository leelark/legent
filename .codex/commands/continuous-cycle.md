# continuous-cycle

Purpose: run the autonomous organization as a closed loop: audit, refine, discover pending work, research, implement, validate, record, and repeat.

Run from repo root:

```powershell
git status --short --branch
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1
powershell -ExecutionPolicy Bypass -File .codex\utilities\list-active-work.ps1
powershell -ExecutionPolicy Bypass -File .codex\utilities\select-next-work.ps1
```

Cycle:

1. Startup: read `AGENTS.md`, `ARCHITECTURE.md`, `PROJECT_CONTEXT.md`, `.codex/bootstrap.md`, `.codex/state/team-state.json`, `.codex/backlog/queue.json`, and active/blocked/risk memory.
2. Recover: if there is valid `IN_PROGRESS`, `REVIEW`, or `VALIDATING` work, resume it before selecting new work.
3. Audit: run `.codex/commands/full-audit.md` when the touched area is unknown or the backlog is stale.
4. Pending scan: run `.codex/commands/pending-scan.md` to extract candidates from risks, debt, blockers, failed fixes, TODOs, validators, reports, and implementation gaps.
5. Research: run `.codex/commands/research-pass.md` for product parity, security, performance, compliance, or current external-source questions.
6. Refine: run `.codex/commands/refine-backlog.md` to turn candidates into scored `READY`, `BACKLOG`, or `BLOCKED` work items.
7. Select: pick the highest-score `READY` item from `.codex/backlog/queue.json`.
8. Assign: use `.codex/agents/routing-matrix.md`; keep up to 6 active parallel subagents only for independent ownership.
9. Checkpoint: create a real checkpoint with `.codex/utilities/new-checkpoint.ps1`.
10. Implement: make the smallest coherent production-quality change.
11. Validate: run the selected validation profile and `.codex/utilities/validate-codex-system.ps1`.
12. Record: update memory, backlog queue, checkpoint, worktree registry/leases, and report files.
13. Repeat until no safe local `READY` work remains or the next action requires external evidence, credentials, production access, or a human decision.

Stop conditions:

- Required secret, credential, provider access, or production evidence is missing.
- All remaining work is `BLOCKED`.
- Validation failure requires a product decision outside the current request.
- The user asks to stop or redirect.

Never read `.env`, private keys, raw credentials, or customer data. Never commit, push, deploy, or claim production readiness unless explicitly requested and evidence gates pass.
