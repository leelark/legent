# Validation Gates

Run from repository root unless noted.

## Backend Focused

```powershell
.\mvnw.cmd -pl <module> -am test
```

Use for service/shared changes. Escalate to full `.\mvnw.cmd test` for shared contracts, security filters, Kafka primitives, or release candidates.

## Frontend Focused

```powershell
cd frontend
npm run lint
npm run build:ci
npm run test:e2e:smoke
```

Add `npm run test:e2e:sanitize`, `npm run test:e2e:visual`, or targeted specs for sanitizer, auth/context, shell, admin/settings, and visible UI changes.

## Route Runtime

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
docker compose config --quiet
kubectl kustomize infrastructure/kubernetes/overlays/production
```

## Security

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
cd frontend
npm audit --omit=dev --audit-level=high
```

CI owns gitleaks and Trivy evidence.

## Production Overlay

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1
```

## Release

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -LocalOnly
```

Strict promotion requires real evidence flags:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireExternalEgressEvidence -ExternalEgressEvidencePath <reviewed-json> -RequireImageDigests -RequireImageEvidence -ImageEvidenceManifest <manifest-json> -RequireGaEvidence -EvidenceDir <evidence-dir>
```

`<reviewed-json>` must be real target-environment reviewed egress evidence. The production egress evidence template is a negative test and must not satisfy strict promotion. Strict egress mode also proves the reviewed external egress NetworkPolicy renders through the production Kustomize overlay.

If evidence is absent, the correct result is blocked, not waived.
