# docs-sync

Purpose: keep docs, memory, prompts, and commands aligned with the actual repo.

Checks:

```powershell
rg -n "scripts\\|scripts/|docs\\|docs/|.codex/reports|TODO|FIXME" AGENTS.md ARCHITECTURE.md PROJECT_CONTEXT.md README.md .codex docs .github -g "!**/node_modules/**"
Test-Path scripts
Test-Path docs
```

When drift is found:

1. Verify the actual path.
2. Update docs or recreate missing utility/docs if still required.
3. Update `.codex/memory/design-decisions.md` and `technical-debt.md` when the drift affects operations.
4. Run impacted validators.
