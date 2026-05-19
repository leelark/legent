# GA Evidence Matrix

Public multi-tenant GA requires target-environment evidence, not local assumptions.

| Evidence | Required artifact |
|---|---|
| Synthetic smoke | Transcript from target environment |
| Live load | High-volume campaign/delivery/tracking/import/webhook load report |
| Restore drill | Restore transcript and RTO/RPO result |
| CI security | Gitleaks and Trivy transcript |
| Filesystem SBOM | SBOM artifact |
| Registry image evidence | Digest, SBOM, signature, provenance manifest |
| Monitoring handoff | Alert route proof and dashboard/runbook ownership |
| TLS certificate | Certificate ownership and ingress TLS evidence |
| Restricted admission | Admission/security policy proof |
| Production egress | Reviewed managed-service/provider CIDR evidence for the current NetworkPolicy generator. FQDN policy evidence requires an approved CNI-specific generator. |

Use:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\write-ga-evidence-manifest-template.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-ga-evidence.ps1 -EvidenceDir <evidence-dir>
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -RequireExternalEgressEvidence -ExternalEgressEvidencePath <reviewed-json> -RequireGaEvidence -EvidenceDir <evidence-dir> -RequireImageDigests -RequireImageEvidence -ImageEvidenceManifest <manifest-json>
```
