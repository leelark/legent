# Image Supply-Chain Evidence Checklist

Generated: 2026-05-17T18:42:32Z
Overlay: `infrastructure/kubernetes/overlays/production`

This checklist is generated from the rendered production overlay. It does not prove registry state by itself; attach registry-backed digest, signature, SBOM, and provenance evidence for each image before promotion.
Use `-ManifestOutputPath` to generate a JSON manifest template, then validate the filled manifest with `validate-image-evidence.ps1 -ManifestPath <path>` and the release gate image evidence hook.

| Image | Digest pinned | SBOM attached | Signature verified | Provenance verified |
| --- | --- | --- | --- | --- |
| `legent/audience-service:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/automation-service:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/campaign-service:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/content-service:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/deliverability-service:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/delivery-service:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/foundation-service:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/frontend:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/identity-service:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/platform-service:1.0.2` | no - attach registry digest evidence | required | required | required |
| `legent/tracking-service:1.0.2` | no - attach registry digest evidence | required | required | required |
