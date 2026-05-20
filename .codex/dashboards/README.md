# Autonomous Dashboards

Dashboards are generated status artifacts for humans and coordinating threads.

Primary dashboard:
- `.codex/dashboards/team-dashboard.md`

Generate with:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\utilities\monitor-autonomous-org.ps1
```

Dashboard files are small and durable. Do not store raw logs, secrets, credentials, or bulky artifacts here.
