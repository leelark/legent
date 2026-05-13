# full-audit

Purpose: repo-wide production-readiness audit.

Suggested commands:

```powershell
git status --short --branch
rg --files | Measure-Object
rg -n "spring\.json\.trusted\.packages|trusted\.packages" services shared config -g "*.yml" -g "*.yaml" -g "*.properties"
rg -n "ddl-auto|SPRING_JPA_HIBERNATE_DDL_AUTO" services shared config docker-compose.yml -g "*.yml" -g "*.yaml" -g "*.properties"
rg -n "@KafkaListener" services shared -g "*.java"
rg -n "TODO|FIXME|HACK|XXX|temporary|workaround" services shared frontend config infrastructure scripts docs -g "!**/target/**" -g "!**/node_modules/**" -g "!**/.next/**"
```

Validation when feasible:

```powershell
.\mvnw.cmd test
cd frontend
npm run lint
npm run test:e2e:smoke
```

Update `.codex/memory/*-map.md`, `technical-debt.md`, `security-findings.md`, and `unresolved-risks.md`.
