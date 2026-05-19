# Security Findings

Fresh baseline date: 2026-05-20.

No confirmed open security findings exist in the fresh memory baseline.

Standing security invariants:
- Never store secrets, `.env` values, raw tokens, private keys, credentials, or customer data in memory.
- Preserve HTTP-only cookie auth and refresh path scoping.
- Preserve unsafe-method origin/referer guard unless replaced with a stronger CSRF strategy.
- Tenant/workspace context must fail closed except documented public endpoints.
- Do not widen Kafka deserialization trust.
- Do not introduce production `ddl-auto=update`.
- Do not weaken HTML sanitization, outbound URL guard, signed tracking URLs, suppression checks, unsubscribe, warmup, rate controls, or inbox safety.

Security changes require focused tests or documented residual risk.
