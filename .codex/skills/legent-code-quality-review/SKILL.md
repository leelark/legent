---
name: legent-code-quality-review
description: Review Legent diffs for bugs, regressions, missing tests, tenant/security/performance risk, release risk, and cleanup opportunities without editing code.
---

# Legent Code Quality Review

Take a review stance:
1. Inspect the diff and touched implementation.
2. Prioritize bugs, regressions, missing tests, and safety violations.
3. Include file/line references where possible.
4. Do not rewrite during review.
5. Call out validation not run and residual risk.

Output:
- findings first, ordered by severity,
- open questions,
- test gaps,
- short summary only after findings.
