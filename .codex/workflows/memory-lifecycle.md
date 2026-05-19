# Memory Lifecycle

## Current-State Files

Keep concise and current:

- `active-work-items.md`
- `blocked-items.md`
- `unresolved-risks.md`
- `repo-map.md`
- `service-dependencies.md`
- `technical-debt.md`
- `performance-bottlenecks.md`
- `security-findings.md`

## History Files

Append only when useful, but summarize aggressively:

- `bug-history.md`
- `root-cause-history.md`
- `successful-fixes.md`
- `failed-fixes.md`
- `release-history.md`
- `design-decisions.md`

## Update Rules

- Use dates and source paths/commands.
- Mark resolved entries as resolved; do not leave contradictory open risks.
- Prefer one durable summary over repeated near-duplicate notes.
- Move long audits to `.codex/reports/` or `docs/audits/`.
- Never store secrets or customer data.
