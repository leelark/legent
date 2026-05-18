# Image Supply Chain Evidence

Date: 2026-05-18
Status: source-side validator and template guidance; not registry evidence

Production promotion requires registry-backed evidence for every rendered `legent/*` image. Do not invent digests or verification results.

Generate the rendered-image checklist and manifest template:

```powershell
.\scripts\ops\write-image-supply-chain-checklist.ps1 -ManifestOutputPath docs\operations\image-evidence-manifest.template.json
```

Validate a filled manifest against the rendered production overlay:

```powershell
.\scripts\ops\validate-image-evidence.ps1 -ManifestPath docs\operations\image-evidence-manifest.json -EvidenceRoot docs\operations\evidence\2026-05-18-rc1
```

The validator checks that the manifest has exactly one entry for each rendered production `legent/*` image and no stale image entries. Strict mode requires digest-pinned rendered images, matching image digests, SBOM evidence with a `sha256` digest, verified signature evidence with a verifier, and verified provenance evidence with builder ID and predicate type.

Template validation is intentionally separate:

```powershell
.\scripts\ops\validate-image-evidence.ps1 -ManifestPath docs\operations\image-evidence-manifest.template.json -AllowTemplatePlaceholders
```

Before GA or production promotion, attach the strict validator transcript to the GA evidence pack under the registry digest, registry SBOM, registry signature, and registry provenance artifacts.
