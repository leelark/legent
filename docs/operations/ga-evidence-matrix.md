# GA Evidence Matrix

Public multi-tenant GA requires target-environment evidence, not local assumptions.

| Evidence | Required artifact |
|---|---|
| Synthetic smoke | Transcript from target environment |
| Live load | High-volume campaign/delivery/tracking/import/webhook load report, including tracking ingress rate-limit proof, observed 429 behavior above policy, accepted/rejected request rates, p95/p99 tracking latency, tracking-service 4xx/5xx, Kafka lag, ClickHouse insert latency, outbox depth/age, dedupe rate, and alert routing |
| Restore drill | Restore transcript and RTO/RPO result |
| CI security | Gitleaks and Trivy transcript |
| Filesystem SBOM | SBOM artifact |
| Registry image evidence | Digest, SBOM, signature, provenance manifest |
| Monitoring handoff | Alert route proof and dashboard/runbook ownership |
| TLS certificate | Certificate ownership and ingress TLS evidence |
| Restricted admission | Admission/security policy proof |
| Production egress | Reviewed managed-service/provider CIDR evidence for the current NetworkPolicy generator. FQDN policy evidence requires an approved CNI-specific generator. |

The GA evidence manifest and registry image evidence manifest must declare `schemaVersion: 1`. Missing or unsupported schema versions fail validation so old manifests cannot satisfy a newer evidence contract by accident.

Production egress evidence must come from the target environment review. Template files, `example-*` values, and documentation CIDRs such as `192.0.2.0/24`, `198.51.100.0/24`, and `203.0.113.0/24` are intentionally rejected by the validator. Strict egress validation also proves the reviewed external egress NetworkPolicy renders through the production Kustomize overlay.

Tracking ingress rate policy is protective configuration, not throughput proof. Current local Nginx posture gives signed open/click/conversion ingestion at `/api/v1/tracking` an elevated `tracking_limit` of `200r/s` with `burst=50 nodelay`; Kubernetes production ingress uses the community ingress-nginx `limit-rps: "200"` annotation for `/api/v1/tracking`. Analytics API and websocket routes remain on the normal `limit-rps: "100"` posture. Ingress-nginx limits are enforced per controller replica, so GA still requires target-environment behavior evidence before any sustained tracking throughput or campaign-volume claim.

Use:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\write-ga-evidence-manifest-template.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-ga-evidence.ps1 -EvidenceDir <evidence-dir>
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireExternalEgressEvidence -ExternalEgressEvidencePath <reviewed-json> -RequireGaEvidence -EvidenceDir <evidence-dir> -RequireImageDigests -RequireImageEvidence -ImageEvidenceManifest <manifest-json>
```
