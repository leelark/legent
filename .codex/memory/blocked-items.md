# Blocked Items

Fresh baseline date: 2026-05-20.

| ID | Owner | Status | Blocker | Next Action |
|---|---|---|---|---|
| production-evidence-pack | RELEASE_MANAGER | BLOCKED | Target-environment evidence is not present in this checkout for image provenance, reviewed egress targets, restore proof, monitoring coverage, TLS/admission controls, and CI/security scan artifacts. Local validator negative fixtures now cover GA/image evidence path, artifact, image-topology, and digest failures. | Collect real target evidence and run `scripts/ops/release-gate.ps1` with strict evidence flags; strict egress mode now also proves reviewed policy render inclusion when evidence is supplied. |
| live-high-volume-proof | PERFORMANCE_ENGINEER | BLOCKED | Local docs and validators cannot prove provider-approved warmed sending capacity for 10 lakh messages in 10 hours. | Run bounded staging/load evidence with warmed domains, provider limits, shard-aware queues, suppression checks, and observability. |
| external-provider-capacity | DELIVERABILITY_ENGINEER | BLOCKED | Provider API/SMTP limits, sender reputation, and domain warmup status are external to the repository. | Verify provider contracts, warmup state, DNS authentication, FBL handling, and rate-control policy before production send claims. |
| campaign-audience-eligibility-final-gate | CAMPAIGN_SERVICE_OWNER | BLOCKED | Audience-side final eligibility is fixed locally, but enforcing an eligibility marker on existing campaign legacy recipient JSON payloads requires a compatibility decision for in-flight/old batches. | Decide whether legacy payloads should fail closed, be grandfathered, or be migrated; then implement the marker contract in `BatchingService` and `SendExecutionService`. |

2026-05-20 audit note: local code gaps were converted to READY/BACKLOG items in `.codex/backlog/queue.json`; external proof requirements remain blocked.
