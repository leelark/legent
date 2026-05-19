---
name: legent-deliverability-safety
description: Work on Legent deliverability, sender authentication, warmup, suppressions, unsubscribe, provider policy, DMARC, feedback loops, reputation, and inbox-safety controls.
---

# Legent Deliverability Safety

1. Never claim guaranteed inbox placement.
2. Treat new sender domains and addresses as unsafe for high-volume sends until authenticated, warmed, monitored, and provider-approved.
3. Preserve suppression, unsubscribe, preference, complaint, bounce, warmup, provider health, and rate-control checks.
4. Verify DNS authentication, DMARC, feedback-loop, reputation, and sender-domain state before high-volume claims.
5. Keep deliverability decisions observable and explainable to operators.
6. Do not bypass inbox safety to satisfy throughput targets.

Validation:

```powershell
.\mvnw.cmd -pl services/deliverability-service -am test
.\mvnw.cmd -pl services/delivery-service -am test
```

Required output:
- sender/domain state considered,
- safety controls preserved,
- provider/rate evidence,
- tests run,
- blocked evidence if external proof is missing.
