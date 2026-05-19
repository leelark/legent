---
name: legent-devops-platform
description: Work on Legent Docker Compose, Kubernetes, CI/security workflow, ops scripts, local runtime, release gates, and production platform behavior.
---

# Legent DevOps Platform

1. Preserve local developer flow and production safety separately.
2. Keep Compose, Nginx, route map, Kubernetes overlays, External Secrets, NetworkPolicy, and CI aligned.
3. Production must not render placeholder secrets or local stateful resources.
4. Use release evidence validators for promotion claims.
5. Do not deploy, push, or mutate external environments unless explicitly requested.

Validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1
docker compose --env-file .env.example config --quiet
kubectl kustomize infrastructure/kubernetes/overlays/production
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -LocalOnly -SkipBackend -SkipFrontend
```

Required output:
- environment surface touched,
- local vs production impact,
- evidence status,
- validation run.
