---
name: legent-sre-incident
description: Work on Legent observability, monitoring, incident response, runbooks, alerts, runtime health, recovery, and operational evidence.
---

# Legent SRE Incident

1. Start from `.codex/workflows/incident-response.md` and relevant runbooks.
2. Identify detection, impact, containment, recovery, prevention, and user-visible status.
3. Add metrics/logs/traces/alerts where failure would otherwise be silent.
4. Keep tracking/analytics pressure isolated from send execution pressure.
5. Record incidents in `incident-history.md` and release implications in `release-history.md`.

Required output:
- signal added or verified,
- failure mode,
- runbook/evidence link,
- validation result,
- residual operational risk.
