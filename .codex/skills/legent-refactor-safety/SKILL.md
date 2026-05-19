---
name: legent-refactor-safety
description: Refactor Legent safely while preserving behavior, tenant isolation, event contracts, deliverability safety, performance, and release quality.
---

# Legent Refactor Safety

1. Map current behavior and call graph.
2. Identify invariants: tenant isolation, idempotency, auth, suppression, warmup, rate control, event contracts, API envelopes, route ownership.
3. Choose the smallest useful refactor boundary.
4. Add characterization tests when behavior is unclear.
5. Refactor in steps that keep the project buildable.
6. Delete replaced code only after verifying routes, tests, consumers, and compatibility exports.
7. Update docs and memory when architecture or workflow changes.

Priorities:
- split large route files and consoles,
- separate backend orchestration responsibilities,
- replace unbounded hot-path logic with chunked/idempotent boundaries,
- remove duplicate/dead code only with validation.

Required output:
- old boundary and new boundary,
- invariants preserved,
- tests/gates run,
- deleted code proof,
- residual debt.
