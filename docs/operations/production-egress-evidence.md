# Production Egress Evidence

Date: 2026-05-16

The production overlay is intentionally fail-closed: it deletes the broad base egress policy, renders `production-default-deny`, and only permits same-namespace pod traffic plus DNS. Do not add guessed provider CIDRs or FQDNs to the repository.

Use `docs/operations/production-egress-evidence.template.json` to create target-environment evidence for reviewed external egress dependencies. Store the completed evidence where the release process expects it, keeping secrets, credentials, raw tokens, and customer data out of the file.

Validate a completed spec directly:

```powershell
.\scripts\ops\validate-production-egress-evidence.ps1 -SpecPath docs\operations\production-egress-evidence.json
```

Require it through the release gate only for target promotion:

```powershell
.\scripts\ops\release-gate.ps1 -RequireExternalEgressEvidence -ExternalEgressEvidencePath docs\operations\production-egress-evidence.json
```

The same gate can be enabled with environment variables:

```powershell
$env:LEGENT_REQUIRE_EXTERNAL_EGRESS_EVIDENCE = "true"
$env:LEGENT_EXTERNAL_EGRESS_EVIDENCE_PATH = "docs\operations\production-egress-evidence.json"
.\scripts\ops\release-gate.ps1
```

Local default release-gate runs do not require this evidence, so the repo-local production overlay can continue to pass with internal-only egress while external provider evidence is still pending.

## Spec Rules

- `schemaVersion` must be `legent.production-egress.v1`.
- The top-level `review.owner`, `review.reviewDate`, and `review.changeTicket` fields are required.
- Each dependency needs `name`, `scope`, `owner`, `reviewDate`, `purpose`, `evidence`, `destinations`, and `ports`.
- `scope` must be `external` or `internal`. Private and reserved CIDRs are rejected for `external` dependencies; use `internal` only for reviewed private endpoints.
- CIDR destinations must be exact reviewed IPv4 CIDRs. `0.0.0.0/0`, placeholders, documentation ranges, RFC1918 private ranges, loopback, link-local, multicast, and other reserved ranges are rejected for external dependencies.
- FQDN destinations must be exact hostnames without schemes, paths, ports, or wildcards.
- Any FQDN destination requires `fqdnPolicy.approved: true` plus a non-placeholder CNI name, policy reference, and note proving the target CNI enforces FQDN egress policy safely.
- Ports must be explicit objects with Kubernetes protocol names: `TCP`, `UDP`, or `SCTP`.

The template intentionally contains placeholders and must fail validation until replaced with real reviewed evidence.
