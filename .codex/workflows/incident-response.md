# Incident Response Workflow

1. Declare incident scope, severity, tenant/workspace blast radius, and owner.
2. Freeze unrelated deploys.
3. Preserve evidence without printing secrets.
4. Identify affected service, route, topic, database, or provider.
5. Stabilize with the smallest reversible mitigation.
6. Verify customer-impacting behavior.
7. Write root cause, timeline, corrective action, and follow-up tests.
8. Update `.codex/memory/incident-history.md`, `root-cause-history.md`, `successful-fixes.md`, and `release-history.md` if applicable.

Incident fixes must not weaken tenant isolation, deliverability compliance, or release gates.
