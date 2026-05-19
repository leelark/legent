# Legent Build And Runtime Commands

This file is current as of 2026-05-20. Older fast-build script references were removed because `scripts/fast-build/*` is not present in this checkout.

## Install

```powershell
.\mvnw.cmd -DskipTests install
cd frontend
npm ci
cd ..
```

## Backend

```powershell
.\mvnw.cmd test
.\mvnw.cmd -pl services/campaign-service -am test
```

## Frontend

```powershell
cd frontend
npm run dev
npm run lint
npm run build:ci
npm run test:e2e:smoke
npm run test:e2e:visual
cd ..
```

## Runtime

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-env.ps1 -EnvFile .env.example -AllowPlaceholders
docker compose config --quiet
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-compose-health.ps1
```

## Release-Oriented Validation

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\test-release-evidence-validators.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1
kubectl kustomize infrastructure/kubernetes/overlays/production
```

## Release Gate

Local dry run with expensive gates skipped:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -LocalOnly -SkipBackend -SkipFrontend -SkipCompose -SkipKustomize
```

Strict production promotion requires real evidence:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireExternalEgressEvidence -ExternalEgressEvidencePath <reviewed-json> -RequireGaEvidence -EvidenceDir <evidence-dir> -RequireImageDigests -RequireImageEvidence -ImageEvidenceManifest <manifest-json>
```

Do not use local-only validation as public-GA evidence.
