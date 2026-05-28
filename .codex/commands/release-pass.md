# release-pass

Purpose: determine release posture. This command can produce `GO`, `NO-GO`, or `BLOCKED-PENDING-EVIDENCE`.

Canonical local gate:

```powershell
git status --short --branch
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -LocalOnly
```

Manual equivalent for diagnosing a failed step:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\test-release-evidence-validators.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-compose-safety.ps1 -ComposeEnvFile .env.example
docker compose --env-file .env.example config --quiet
kubectl kustomize infrastructure/kubernetes/overlays/production
.\mvnw.cmd -T1 test
cd frontend
npm run lint
npm run test:coverage
npm run build:ci
npm run test:e2e:visual
npm run test:e2e:chromium
npm run test:e2e:smoke
npm audit --omit=dev --audit-level=high
cd ..
```

Strict promotion checks:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireExternalEgressEvidence -ExternalEgressEvidencePath <reviewed-json> -RequireGaEvidence -EvidenceDir <evidence-dir> -RequireImageDigests -RequireImageEvidence -ImageEvidenceManifest <manifest-json>
```

Use target-environment reviewed egress evidence for `<reviewed-json>`, not `docs/operations/production-egress-evidence.template.json`. Strict egress mode validates the evidence, generates or checks the reviewed external egress NetworkPolicy, and proves it renders through the production Kustomize overlay.

Release is blocked if any of these are absent:

- Target external egress evidence.
- Live synthetic smoke evidence.
- Live high-volume load evidence.
- Restore drill evidence.
- CI gitleaks/Trivy transcript.
- Filesystem and registry SBOM evidence.
- Registry digest, signature, and provenance evidence.
- TLS/certificate ownership evidence.
- Restricted admission proof.
- Monitoring/alert delivery evidence.

Update `release-history.md` with date, commands, results, residual risks, and release decision.
