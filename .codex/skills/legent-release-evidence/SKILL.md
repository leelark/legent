---
name: legent-release-evidence
description: Evaluate or build Legent release readiness. Use for CI, Kubernetes production overlays, Docker Compose, route validation, image evidence, egress evidence, GA evidence, release gates, or production promotion decisions.
---

# Legent Release Evidence

1. Start from `.codex/commands/release-pass.md`.
2. Validate route map, repository artifact hygiene, production overlay, Compose config, and Kustomize render.
3. Require real target evidence for egress, image digest/SBOM/signature/provenance, live smoke, load, restore, TLS/admission, and monitoring.
4. Treat missing evidence as `BLOCKED-PENDING-EVIDENCE`, not pass.
5. Update `release-history.md`, `blocked-items.md`, and `unresolved-risks.md`.

Never deploy or push unless explicitly requested and gates pass.

## Evidence Checks

- Production image evidence must cover every production image and match pinned digests when strict mode is used.
- GA evidence paths must stay inside the evidence directory.
- Egress evidence must reject broad CIDRs, malformed ports, unsupported protocol values, and FQDN-only NetworkPolicy assumptions.
- Local-only gate success is not a production promotion.

## Required Output

- Gate command and result.
- Evidence present and evidence missing.
- Release posture: `PASS`, `LOCAL-ONLY`, or `BLOCKED-PENDING-EVIDENCE`.
- Memory updates in release, blocked, and unresolved-risk files.
