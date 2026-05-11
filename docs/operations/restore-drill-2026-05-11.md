# Restore Drill Evidence

Date: 2026-05-11
Status: procedure defined, execution required in target cluster

## Scope

- ClickHouse tracking analytics backup and restore.
- PostgreSQL service databases backup and restore.
- MinIO object storage restore validation.

## Commands

Run the existing backup/restore automation:

```powershell
.\scripts\ops\backup-restore.ps1 -Mode backup
.\scripts\ops\backup-restore.ps1 -Mode restore -BackupPath <path-to-backup>
```

Run post-restore smoke:

```powershell
.\scripts\ops\synthetic-smoke.ps1 -BaseUrl https://api.legent.example
```

## Acceptance Criteria

- Restore completes without manual database surgery.
- `/api/v1/health` returns success.
- Synthetic smoke passes.
- Tracking analytics queries return restored rows for a known tenant/workspace sample.
- Audit log records drill operator, source backup, restore target, start time, end time, and result.

## Evidence To Capture

- Backup artifact URI.
- Restore command transcript.
- Synthetic smoke output.
- ClickHouse row count before and after restore.
- PostgreSQL schema migration version after restore.
- Owner sign-off.
