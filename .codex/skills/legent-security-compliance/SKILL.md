---
name: legent-security-compliance
description: Work on Legent security, privacy, tenant isolation, auth, public endpoints, signed tracking, HTML sanitization, secrets posture, and compliance-sensitive flows.
---

# Legent Security Compliance

1. Read `AGENTS.md`, `.codex/memory/security-findings.md`, `.codex/memory/unresolved-risks.md`, and the touched implementation.
2. Preserve cookie auth, refresh path scoping, origin/referer guard, tenant/workspace fail-closed behavior, SCIM token/scope checks, signed tracking, HTML sanitization, outbound URL guard, suppression, unsubscribe, warmup, and rate controls.
3. Do not add `permitAll` routes unless intentionally public, documented, and tested.
4. Do not widen Kafka deserialization trust or production `ddl-auto` behavior.
5. Do not read, print, transform, or commit `.env`, keys, credentials, tokens, or customer data.

Validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
.\mvnw.cmd -pl <module> -am test
```

Required output:
- security boundary touched,
- allowed and denied cases tested,
- residual risk,
- memory updates.
