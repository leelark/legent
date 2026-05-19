# fix-runtime

Purpose: diagnose and repair local startup/runtime failures.

Use `.env.example` for validation by default. Do not read `.env` unless the user explicitly requests a local secret-audit workflow.

Baseline:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-env.ps1 -EnvFile .env.example -AllowPlaceholders
docker compose config --quiet
```

If dependencies changed:

```powershell
.\mvnw.cmd -DskipTests install
cd frontend
npm ci
cd ..
```

Start and inspect:

```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-compose-health.ps1
docker compose ps
```

Repair loop:

1. Capture failing service, healthcheck, and non-secret error summary.
2. Identify owning module and recent changes.
3. Fix root cause, not only timeout/healthcheck symptoms.
4. Rebuild impacted service.
5. Rerun health and focused tests.
6. Update `root-cause-history.md`, `successful-fixes.md`, `failed-fixes.md`, and `release-history.md` as applicable.
