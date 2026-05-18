# Production Egress Evidence

Date: 2026-05-16

The production overlay is intentionally fail-closed: it deletes the broad base egress policy, renders `production-default-deny`, and only permits same-namespace pod traffic plus DNS. Do not add guessed provider CIDRs or FQDNs to the repository.

Use `docs/operations/production-egress-evidence.template.json` to create target-environment evidence for reviewed external egress dependencies. Store the completed evidence where the release process expects it, keeping secrets, credentials, raw tokens, and customer data out of the file.

Validate a completed spec directly:

```powershell
.\scripts\ops\validate-production-egress-evidence.ps1 -SpecPath docs\operations\production-egress-evidence.json
```

Direct validation checks the reviewed dependency spec. For target promotion, `release-gate.ps1` adds strict checks that require `policyEvidence.renderedArtifacts` and `policyEvidence.appliedEvidence` to reference real local artifacts or immutable artifact URIs.

Render reviewed policy artifacts from a completed spec:

```powershell
.\scripts\ops\write-production-egress-policy.ps1 -SpecPath docs\operations\production-egress-evidence.json -OutputDirectory docs\operations\production-egress-rendered
```

The renderer consumes only reviewed evidence. It writes a Kubernetes `NetworkPolicy` for CIDR destinations and, when `fqdnPolicy.cni` is `cilium`, a concrete `CiliumNetworkPolicy` for exact FQDN destinations. For other approved CNIs it writes an explicit FQDN review JSON so the network owner can materialize the equivalent provider-specific policy outside the repository. Use `-RequireConcreteFqdnPolicy` to fail when FQDN evidence exists but the source-side renderer cannot produce a concrete CNI policy.

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
- Strict promotion validation requires `policyEvidence.renderedArtifacts` and `policyEvidence.appliedEvidence`. Local paths must exist and must not point to secret/env-like files; immutable artifact URIs are accepted without repository-side fetching.
- Each dependency needs `name`, `scope`, `owner`, `reviewDate`, `purpose`, `evidence`, `destinations`, and `ports`.
- `scope` must be `external` or `internal`. Private and reserved CIDRs are rejected for `external` dependencies; use `internal` only for reviewed private endpoints.
- CIDR destinations must be exact reviewed IPv4 CIDRs. `0.0.0.0/0`, placeholders, documentation ranges, RFC1918 private ranges, loopback, link-local, multicast, and other reserved ranges are rejected for external dependencies.
- FQDN destinations must be exact hostnames without schemes, paths, ports, or wildcards.
- Any FQDN destination requires `fqdnPolicy.approved: true` plus a non-placeholder CNI name, policy reference, and note proving the target CNI enforces FQDN egress policy safely.
- Ports must be explicit objects with Kubernetes protocol names: `TCP`, `UDP`, or `SCTP`.
- Generated policy artifacts are evidence-derived review outputs, not proof that the target cluster has applied them. Promotion still needs the rendered artifact, apply/admission transcript, and CNI enforcement proof from the target environment.

The template intentionally contains placeholders and must fail validation until replaced with real reviewed evidence.
