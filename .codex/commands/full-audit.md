# full-audit

Purpose: repo-wide production-readiness audit.

Discovery:

```powershell
git status --short --branch
rg --files --hidden -g "!**/node_modules/**" -g "!**/target/**" -g "!**/.next/**"
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
npm run build:ci
npm run test:e2e:smoke
cd ..
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1
docker compose config --quiet
kubectl kustomize infrastructure/kubernetes/overlays/production
```

Report to `.codex/reports/` and update memory:

- `unresolved-risks.md`
- `technical-debt.md`
- `security-findings.md`
- `performance-bottlenecks.md`
- `repo-map.md`
- `service-dependencies.md`

Keep external evidence gaps blocked. Do not infer release readiness from local-only checks.
