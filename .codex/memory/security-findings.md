# Security Findings

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Completed local security fixes were removed from current-state memory. Detailed history remains in queue doneWork/checkpoints.

| Area | Current finding | Queue items | Next action |
|---|---|---|---|
| Production release | Strict evidence is absent; local validators are not release proof. | `production-evidence-pack` and child evidence items | Collect target evidence and run strict release gate. |
| Tenant lifecycle | Platform-admin versus tenant self-management policy is unresolved. | `foundation-tenant-lifecycle-policy` | Decide policy, then implement RBAC/scoping tests. |
| Public contact PII | Tenant/workspace inbox mapping, retention, and migration are unresolved. Contact lifecycle audit now stores hashed email and bounded metadata locally, but target retention/retrieval policy evidence is still pending. | `foundation-public-contact-tenant-inbox` plus future audit-retention evidence | Decide schema/product mapping before exposing inbox; validate audit retention and retrieval policy before release claims. |
| Automation live operations | Live activity runs now have local lock, retry-after, and reason-required override controls; production audit/RBAC evidence is still pending. Script execution must remain blocked pending signed sandbox/no-ambient-secret design. | `automation-script-activity-security-sandbox` plus release evidence items | Validate target audit/RBAC evidence and approve sandbox/runtime restrictions before enabling scripts. |
| Service identity | Internal credential guards remain the baseline; audience-resolution chunk reads, deliverability suppression internal history/bulk-check routes, audience import/data-extension internal operations, and content render/snapshot/send-governance internal reads now add signed caller/action/tenant/workspace headers with service allowlists. Full platform identity, mTLS, NetworkPolicy proof, and future internal-route drift coverage are still pending. | future service identity expansion / release evidence | Keep route-specific signed identity on future internal routes, then collect target mTLS/NetworkPolicy evidence. |
| AI data use | Provider contract/metering ledger and reviewed draft apply UX exist locally with hash/reference-only evidence; live provider-backed AI still needs workflow-specific disclosure acceptance, cross-service audit verification, data minimization, credential handling, and runtime evidence. | AI runtime items | Keep provider calls disabled until workflow-specific gates and evidence pass. |
| Target auth evidence | Credentialed target login/context smoke requires secure test credentials. | `qa-auth-target-login-smoke` | Supply CI/test account secrets securely. |

Standing invariants: preserve HTTP-only cookie auth, tenant/workspace fail-closed behavior, origin/referer guard, Kafka trust allowlist, signed tracking, sanitization, suppression, warmup, rate controls, immutable send-governance policy evidence before delivery dispatch, and no secret storage in memory.
