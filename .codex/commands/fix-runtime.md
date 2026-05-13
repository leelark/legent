# fix-runtime

Purpose: diagnose and repair local startup/runtime failures.

Use `.env.example`, not `.env`, unless user explicitly asks for secret audit or runtime validation against local secrets.

Commands:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-env.ps1 -EnvFile .env.example -AllowPlaceholders
docker compose config --quiet
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-compose-health.ps1
```

If failure occurs:

1. Capture service name, failing healthcheck, and last non-secret stack trace.
2. Check owning module and recent changes.
3. Fix root cause, not healthcheck timeout only.
4. Rebuild impacted service.
5. Record failure and fix in `.codex/memory/root-cause-history.md`, `failed-fixes.md`, or `successful-fixes.md`.
