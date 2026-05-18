# release-pass

Purpose: release-readiness gate.

Current expectation, 2026-05-17: the default production overlay validator and
default `release-gate.ps1` are expected to be locally passable when normal
tooling, `.env` preflight inputs, Docker, Kubernetes CLI, backend, and frontend
prerequisites are available. Default `release-gate.ps1` already runs backend
Maven `clean package -T 1C`, frontend lint, sanitizer regression, production
build, smoke E2E, visual E2E, Compose config, Kustomize renders, and production
overlay drift checks.

Strict evidence modes are intentionally fail-closed until real target evidence
is attached:

- `-RequireExternalEgressEvidence` requires reviewed production managed-service
  CIDR/port evidence or approved CNI FQDN policy evidence.
- `-RequireGaEvidence -EvidenceDir <dir>` requires a current GA evidence
  manifest and local artifacts.
- `-RequireImageDigests` requires digest-pinned rendered `legent/*` images.
- `-RequireImageEvidence -ImageEvidenceManifest <json>` requires digest,
  SBOM, signature, and provenance evidence for rendered images.

Commands:

```powershell
git status --short --branch
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\test-release-evidence-validators.ps1
docker compose config --quiet
kubectl kustomize infrastructure/kubernetes/overlays/production
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1
```

Optional strict evidence checks before promotion:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireExternalEgressEvidence -ExternalEgressEvidencePath <reviewed-json>
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireGaEvidence -EvidenceDir <evidence-dir>
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireImageDigests -RequireImageEvidence -ImageEvidenceManifest <image-evidence-json>
```

Do not commit, push, or create release unless user explicitly asks and relevant gates pass.
