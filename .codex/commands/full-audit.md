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
npm run build:ci
npm run test:e2e:smoke
cd ..
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-env.ps1 -EnvFile .env.example -AllowPlaceholders
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\test-release-evidence-validators.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
docker compose config --quiet
kubectl kustomize infrastructure/kubernetes/overlays/production
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1
```

Strict evidence checks when production promotion is being evaluated:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireExternalEgressEvidence -ExternalEgressEvidencePath <reviewed-json>
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireGaEvidence -EvidenceDir <evidence-dir>
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireImageDigests -RequireImageEvidence -ImageEvidenceManifest <image-evidence-json>
```

Update `.codex/memory/*-map.md`, `technical-debt.md`, `security-findings.md`, and `unresolved-risks.md`.
