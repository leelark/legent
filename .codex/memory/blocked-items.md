# Blocked Items

Fresh baseline date: 2026-05-20.

| ID | Owner | Status | Blocker | Next Action |
|---|---|---|---|---|
| production-evidence-pack | RELEASE_MANAGER | BLOCKED | Target-environment evidence is not present in this checkout for image provenance, egress review, restore proof, monitoring coverage, TLS/admission controls, and CI/security scan artifacts. | Collect real target evidence and run `scripts/ops/release-gate.ps1` with strict evidence flags. |
| live-high-volume-proof | PERFORMANCE_ENGINEER | BLOCKED | Local docs and validators cannot prove provider-approved warmed sending capacity for 10 lakh messages in 10 hours. | Run bounded staging/load evidence with warmed domains, provider limits, shard-aware queues, suppression checks, and observability. |
| external-provider-capacity | DELIVERABILITY_ENGINEER | BLOCKED | Provider API/SMTP limits, sender reputation, and domain warmup status are external to the repository. | Verify provider contracts, warmup state, DNS authentication, FBL handling, and rate-control policy before production send claims. |
