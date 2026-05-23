# Security Findings

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Completed local security fixes were removed from current-state memory. Detailed history remains in queue doneWork/checkpoints.

| Area | Current finding | Queue items | Next action |
|---|---|---|---|
| Production release | Strict evidence is absent; local validators are not release proof. | `production-evidence-pack` and child evidence items | Collect target evidence and run strict release gate. |
| Tenant lifecycle | Platform-admin versus tenant self-management policy is unresolved. | `foundation-tenant-lifecycle-policy` | Decide policy, then implement RBAC/scoping tests. |
| Public contact PII | Tenant/workspace inbox mapping, retention, and migration are unresolved. | `foundation-public-contact-tenant-inbox` | Decide schema/product mapping before exposing inbox. |
| Automation scripts | Script execution must remain blocked pending signed sandbox/no-ambient-secret design. | `automation-script-activity-security-sandbox` | Approve sandbox and runtime restrictions. |
| Service identity | Internal token guards exist, but stronger service identity evidence is pending. | `service-to-service-identity-hardening` | Design mTLS/service JWT posture. |
| AI data use | Provider-backed AI needs disclosure, policy, metering, kill switch, human review, and data-class controls. | `ai-provider-contract-metering` and AI runtime items | Implement governance before provider calls. |
| Target auth evidence | Credentialed target login/context smoke requires secure test credentials. | `qa-auth-target-login-smoke` | Supply CI/test account secrets securely. |

Standing invariants: preserve HTTP-only cookie auth, tenant/workspace fail-closed behavior, origin/referer guard, Kafka trust allowlist, signed tracking, sanitization, suppression, warmup, rate controls, and no secret storage in memory.
