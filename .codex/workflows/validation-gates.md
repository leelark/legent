# Validation Gates

Run from repository root unless noted.

## Backend Focused

```powershell
.\mvnw.cmd -pl <module> -am test
```

Use for service/shared changes. Escalate to full `.\mvnw.cmd test` for shared contracts, security filters, Kafka primitives, or release candidates.

## Backend Integration

Integration tests use the `*IT`, `*ITCase`, or `*IntegrationTest` naming contract and run through Maven Failsafe, not Surefire.

```powershell
.\mvnw.cmd -DskipITs=false verify
.\mvnw.cmd -pl <module> -am -DskipITs=false verify
```

Plain `.\mvnw.cmd test` remains the unit/service gate and excludes the integration-test naming contract. Testcontainers-backed tests must use the integration naming contract unless there is a deliberate reason for Docker-dependent behavior in the unit gate.

## Coverage

Backend coverage uses JaCoCo through the Maven `coverage` profile. The current baseline enforces conservative per-module thresholds in `pom.xml`: instruction 1%, line 1%, method 1%, and branch 0%. The summary validator also reports aggregate coverage for CI readability.

```powershell
.\mvnw.cmd -T1 -Pcoverage -DskipITs=true verify
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-jacoco-coverage.ps1
.\mvnw.cmd -T1 -Pcoverage,integration-tests -DskipITs=false verify
```

Frontend coverage uses Vitest/V8 with conservative initial thresholds in `frontend/vitest.config.ts`; the current baseline is 0.1% for statements, branches, functions, and lines.

```powershell
cd frontend
npm run test:coverage
```

Artifacts: backend reports are under `services/*/target/site/jacoco/` and `shared/*/target/site/jacoco/`; frontend reports are under `frontend/coverage/` and ignored locally. Backend coverage gates force `-T1` because JaCoCo instrumentation plus the repo default `-T1C` reactor can starve local HTTP/reactive tests. Exclusions are limited to bootstrap applications, generated mappers, DTO/request/response data types, and repositories so service, security, policy, and runtime logic remain measurable. Raise thresholds only after fresh baseline evidence passes locally and in CI.

## Frontend Focused

```powershell
cd frontend
npm run lint
npm run build:ci
npm run test:e2e:chromium
npm run test:e2e:smoke
```

Use `npm run test:e2e:chromium` as the full Chromium suite gate for frontend release/CI readiness. Keep `npm run test:e2e:smoke`, `npm run test:e2e:sanitize`, `npm run test:e2e:visual`, or targeted specs for fast local feedback on sanitizer, auth/context, shell, admin/settings, and visible UI changes.

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
