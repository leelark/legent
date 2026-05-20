---
name: legent-code-quality-cleanup
description: Run efficient Legent code-quality cleanup passes for dead code, duplicate logic, large-file splitting, TODO triage, unused exports, unreachable routes, formatting drift, and safe deletion proof.
---

# Legent Code Quality Cleanup

1. Identify one narrow cleanup scope.
2. Read call sites and tests before deleting or moving code.
3. Prefer behavior-preserving changes.
4. Prove deletion with `rg`, build/test references, route checks, or compiler/lint evidence.
5. Avoid broad refactors while product work is active in overlapping leases.
6. Update `technical-debt.md` only with durable debt changes; put detailed cleanup activity in audit JSONL.

Validation:
- focused Maven or frontend lint/build,
- route validation when routes/config change,
- `git diff --check`.
