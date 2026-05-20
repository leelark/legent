---
name: legent-autonomous-qa
description: Orchestrate autonomous QA for Legent changes: test impact analysis, validation sequencing, smoke/regression selection, flake handling, browser evidence, QA signoff, and residual-risk reporting.
---

# Legent Autonomous QA

1. Read changed files, work item, acceptance criteria, and validation profile.
2. Select the smallest meaningful test set, then broaden when shared/security/performance behavior changed.
3. Separate failed validation from flaky infrastructure.
4. Capture browser/Playwright evidence for visible UI changes when feasible.
5. Record validation in handoff, audit events, and the relevant memory target.

Do not mark work done if required validation failed or was skipped without reason.
