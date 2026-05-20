---
name: legent-dependency-hygiene
description: Manage Legent Maven, npm, container, CI, SBOM, license, and CVE dependency hygiene. Use for upgrades, audit findings, lockfile drift, dependency convergence, and supply-chain posture.
---

# Legent Dependency Hygiene

1. Identify dependency owner: Maven, npm, container, CI action, Kubernetes image, or tool.
2. Prefer minimal upgrades that resolve the concrete issue.
3. Do not add dependencies unless they reduce real complexity or match project patterns.
4. Update lockfiles only when dependency changes require it.
5. Check license/security posture and compatibility.
6. Validate with focused build/test/audit commands.

Memory:
- durable dependency decisions go to `dependency-map.md` or `security-findings.md`;
- detailed audit output goes to reports/audit events.
