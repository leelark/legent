# pending-scan

Purpose: discover executable work without relying on old memory.

Read:

- `.codex/memory/unresolved-risks.md`
- `.codex/memory/technical-debt.md`
- `.codex/memory/blocked-items.md`
- `.codex/memory/failed-fixes.md`
- `.codex/reports/`
- `docs/audits/`
- CI and ops scripts
- implementation sources for the requested area

Useful scans:

```powershell
rg -n "TODO|FIXME|HACK|XXX|temporary|workaround" services shared frontend config infrastructure scripts docs -g "!**/target/**" -g "!**/node_modules/**" -g "!**/.next/**"
rg -n "permitAll|ddl-auto|trusted\.packages|catch \\(Exception|TODO" services shared config -g "*.java" -g "*.yml" -g "*.yaml" -g "*.properties"
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1
```

Output each candidate with:

- source evidence,
- area,
- risk,
- proposed owner,
- likely files,
- blocker status,
- validation profile,
- suggested priority dimensions.

Feed candidates into `.codex/commands/refine-backlog.md`.
