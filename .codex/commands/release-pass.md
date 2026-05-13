# release-pass

Purpose: release-readiness gate.

Commands:

```powershell
git status --short --branch
.\mvnw.cmd test
cd frontend
npm run lint
npm run build
npm run test:e2e:smoke
cd ..
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
docker compose config --quiet
kubectl kustomize infrastructure/kubernetes/overlays/production
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1
```

Do not commit, push, or create release unless user explicitly asks and relevant gates pass.
