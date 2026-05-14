# Blocked Items

Last updated: 2026-05-13.

- Production egress blocker, 2026-05-13, source `infrastructure/kubernetes/base/network-policy.yml`, `infrastructure/kubernetes/overlays/production/network-policy.yml`, and `scripts/ops/validate-production-overlay.ps1`: production renders default-deny plus inherited broad base `allow-legent-egress` (`0.0.0.0/0` TCP 443 except private ranges). Release validation now fails closed when this broad policy renders. Impact: replacing it safely requires exact managed-service/provider CIDRs and ports, or confirmed CNI FQDN egress support, so production promotion remains blocked until infrastructure policy is reviewed. Next action: obtain exact service dependencies, provider CIDRs/ports, or confirm the selected CNI supports reviewed FQDN egress policies before writing policy. Do not guess CIDRs or FQDN rules.
- Loop 5 production overlay note, 2026-05-13, source `infrastructure/kubernetes/overlays/production/external-secrets.yml` and `scripts/ops/validate-production-overlay.ps1`: required runtime secret keys now render and are checked. Remaining release blocker is still only the reviewed egress policy data above; do not weaken fail-closed validation to pass release.
- Full runtime validation may require Docker/Kubernetes availability and enough local resources; record exact blocker if encountered.
