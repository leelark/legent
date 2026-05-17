# Final Audit Report - 2026-05-16

Status: not released.
Branch: `main...origin/main`.
Scope: consolidated final audit plus the current release-blocker implementation wave requested on 2026-05-16.

## Executive Result

All locally implementable blocker work is now encoded as fail-closed validation, preflight, or evidence intake. No production CIDRs, FQDN policy approval, registry digests, provenance, live smoke/load transcripts, restore proof, TLS ownership, or admission proof were invented from source. Those remain external release inputs.

The repo now has a single current report in `.codex/reports`: this file. Older superseded audit/report files were removed after current facts were merged into durable memory and this report.

## Implemented Since Prior Audit

- Production external egress evidence is now validated by `scripts/ops/validate-production-egress-evidence.ps1`, documented in `docs/operations/production-egress-evidence.md`, templated in `docs/operations/production-egress-evidence.template.json`, and wired into `scripts/ops/release-gate.ps1` behind `-RequireExternalEgressEvidence` or `LEGENT_REQUIRE_EXTERNAL_EGRESS_EVIDENCE`.
- Audience V17 operator mapping now has an offline validator and regression harness: `scripts/ops/validate-audience-data-extension-workspace-mapping.ps1` and `scripts/ops/test-audience-data-extension-workspace-mapping.ps1`. It rejects placeholder workspace IDs, duplicates, malformed CSV/JSON, and missing reviewed metadata before production migration.
- GA evidence now has a strict manifest validator: `scripts/ops/validate-ga-evidence.ps1`, wired into `release-gate.ps1` through `-EvidenceDir`, `-RequireGaEvidence`, and `LEGENT_REQUIRE_GA_EVIDENCE`.
- Live scale proof now has structured evidence intake in `scripts/load/phase3-high-volume-load.ps1`. `-RequireLive` requires non-synthetic inputs and `-LiveEvidencePath` proof for ClickHouse TTL/partitioning, scheduled Postgres purge, remote render latency, Kafka handoff pressure, and delivery provider capacity. Evidence output is sanitized.
- Image supply-chain evidence is now stricter. `scripts/ops/validate-production-overlay.ps1` supports `-RequireImageDigests` and `-RequireImageEvidence`; `scripts/ops/write-image-supply-chain-checklist.ps1` emits a manifest template; `release-gate.ps1` wires strict image digest/evidence flags and envs.
- CI now uploads both the rendered image supply-chain checklist and manifest template from `.github/workflows/ci-security.yml`.
- Prior local fixes remain in the worktree: provider/domain/platform RBAC, campaign atomic batch claim and retry requeue fail-closed behavior, tracking raw-event retention scheduling, and legacy tracking writer removal.

## Validation Run

Passed:

- PowerShell parser checks for all touched release/load/evidence scripts.
- `powershell -ExecutionPolicy Bypass -File scripts\ops\test-audience-data-extension-workspace-mapping.ps1`
- `powershell -ExecutionPolicy Bypass -File scripts\load\phase3-high-volume-load.ps1 -DryRun -Imports 0 -Segments 0 -Sends 1 -TrackingEvents 1 -Reports 1`
- `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1`
- `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1`
- `powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -SkipBackend -SkipFrontend -SkipE2E -SkipEnvValidation -SkipVisualE2E`

Expected fail-closed checks:

- `validate-production-overlay.ps1 -RequireImageDigests` fails on current `legent/*:1.0.2` tag-only images.
- `phase3-high-volume-load.ps1 -RequireLive` fails without `-LiveEvidencePath` target proof.
- `validate-production-egress-evidence.ps1 -SpecPath docs\operations\production-egress-evidence.template.json` fails on placeholders.
- `validate-ga-evidence.ps1 -EvidenceDir docs\operations` fails because no GA evidence manifest is attached there.
- `release-gate.ps1` strict hooks for GA evidence, external egress evidence, and image digests all fail closed with missing/current placeholder evidence.

Prior validation still applies from the earlier 2026-05-16 final audit wave:

- Focused Maven reactor over delivery, deliverability, campaign, platform, and tracking passed.
- `docker compose config --quiet` passed through release-gate.
- Kubernetes production/global/observability Kustomize renders passed through release-gate.
- `git diff --check` had CRLF normalization warnings only in worker validation.

## Remaining Release Blockers

- External egress: attach reviewed production managed-service/provider CIDRs and ports, or approved CNI FQDN-policy evidence, then run `release-gate.ps1 -RequireExternalEgressEvidence -ExternalEgressEvidencePath <reviewed-json>`.
- Audience V17: run staging export/preflight, review target workspace mapping, generate reviewed SQL for `audience_data_extension_workspace_mapping_review`, and apply only after authoritative workspace verification.
- Live GA evidence: attach a GA evidence manifest containing target synthetic smoke, live load, restore drill, CI gitleaks, Trivy, SBOM, alert routing, TLS/cert ownership, restricted admission, registry digest, registry signature, and registry provenance artifacts.
- Tracking/campaign scale: run target-like load with real seeded campaigns/data extensions and attach live evidence for ClickHouse TTL/partitioning, Postgres purge, remote render pressure, Kafka handoff, and provider capacity.
- Images: replace tag-only `legent/*:1.0.2` references with digest-pinned images and attach registry SBOM/signature/provenance evidence before strict digest/evidence validation can pass.

## Cleaned Reports

Removed as superseded by this final report and current memory:

- `docs-scripts-drift-2026-05-16.md`
- `full-audit-2026-05-16.yaml`
- `full-audit-2026-05-16-wave2.yaml`
- `ga-evidence-matrix-2026-05-13.md`
- `loop5-final-memory-infra-2026-05-16.md`
- `production-blocker-wave-2026-05-16.yaml`
- `repository-intelligence-2026-05-13.md`
- `production-egress-evidence-validation-2026-05-16.md`

## Next Action

Collect real target-environment evidence, rerun release gate with strict evidence flags, then run the full backend/frontend/security/load gate before any GA or production promotion claim.
