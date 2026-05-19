---
name: legent-docs-memory
description: Maintain Legent documentation, AGENTS/architecture/context files, .codex memory, reports, prompts, commands, and workflow drift.
---

# Legent Docs Memory

1. Treat `.codex/memory` as fresh baseline from 2026-05-20.
2. Record only verified current facts with source file or command.
3. Keep active memory concise; move long audits to `.codex/reports/` or `docs/audits/`.
4. Do not store secrets, `.env` values, raw logs with credentials, tokens, private keys, or customer data.
5. Keep root docs, commands, workflows, skills, utilities, and validators aligned.
6. Use `.codex/utilities/validate-codex-system.ps1` after changing `.codex`.

Required output:
- docs/memory changed,
- source of facts,
- stale content removed,
- validator result,
- residual drift risk.
