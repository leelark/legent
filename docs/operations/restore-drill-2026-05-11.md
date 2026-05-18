# Restore Drill Evidence

Date: 2026-05-11
Status: procedure defined, execution required in target cluster

## Scope

- ClickHouse tracking analytics backup and restore.
- PostgreSQL service databases backup and restore.
- MinIO object storage restore validation.

## Commands

Run the existing backup/restore automation. Backups may contain customer data; use the default external backup directory or set `$env:LEGENT_BACKUP_DIR` outside the repository.

```powershell
.\scripts\ops\backup-restore.ps1 -Target postgres -Action backup
.\scripts\ops\backup-restore.ps1 -Target postgres -Action restore -InputFile <postgres-backup>
.\scripts\ops\backup-restore.ps1 -Target clickhouse -Action backup
.\scripts\ops\backup-restore.ps1 -Target clickhouse -Action drill -InputFile <clickhouse-backup>
.\scripts\ops\backup-restore.ps1 -Target clickhouse -Action restore -InputFile <clickhouse-backup>
.\scripts\ops\backup-restore.ps1 -Target minio -Action backup
.\scripts\ops\backup-restore.ps1 -Target minio -Action restore -InputFile <minio-backup>
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

Use `docs/operations/restore-drill-evidence-transcript.template.json` for the release evidence wrapper. The completed JSON must pass:

```powershell
.\scripts\ops\validate-evidence-transcript.ps1 -TranscriptPath .\docs\operations\evidence\<release>\restore-drill-evidence-transcript.json -Type restore-drill -MaxAgeDays 14
```

For GA evidence, reference the completed transcript and sanitized supporting files from `ga-evidence-manifest.json` as the `restore-drill` artifact. Keep backup archives outside the evidence directory; include only artifact URIs, checksums, aggregate row/object counts, RPO/RTO, command timestamps, and pass statuses.
