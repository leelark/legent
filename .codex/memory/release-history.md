# Release History

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

No production release has been recorded in this fresh baseline. Local fixes and validations are internal readiness work only.

## Current Release Posture

- Status: BLOCKED-PENDING-EVIDENCE.
- Local gates recently observed passing in the audit: Codex validation/monitor, route map, repo artifact hygiene, production overlay, Docker Compose config, production Kustomize render, local-only release gate, full Maven tests, frontend lint, and frontend build.
- Non-passing/not-claimable: release evidence validator self-test timed out in the latest audit run; strict release gate cannot run without external evidence.
- Required before promotion: production evidence pack, image digest/provenance, reviewed egress evidence, GA smoke/restore/monitoring/admission evidence, provider/high-volume proof, target auth/runtime evidence, CI/security transcript, and documented rollback/handoff.

Detailed local change history is stored in .codex/backlog/queue.json doneWork, checkpoints, and audit events.
