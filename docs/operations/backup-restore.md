# Backup And Restore

All backup commands are environment driven. Do not place credentials in scripts or Git. Provide credentials through Kubernetes secrets, the current shell, or the cluster auth context.

Backups may contain customer data. By default, the script writes to a `legent-backups` directory under `$HOME`, or to `$env:LEGENT_BACKUP_DIR` when set. Prefer storage outside the repository. Repo-local `.\backups` is git-ignored for short-lived local drills only.

## Postgres

Backup:

```powershell
.\scripts\ops\backup-restore.ps1 -Target postgres -Action backup
```

Restore:

```powershell
.\scripts\ops\backup-restore.ps1 -Target postgres -Action restore -InputFile <postgres-backup>
```

## ClickHouse

Backup:

```powershell
.\scripts\ops\backup-restore.ps1 -Target clickhouse -Action backup
```

Restore:

```powershell
.\scripts\ops\backup-restore.ps1 -Target clickhouse -Action restore -InputFile <clickhouse-backup>
```

Drill:

```powershell
.\scripts\ops\backup-restore.ps1 -Target clickhouse -Action drill -InputFile <clickhouse-backup>
```

## MinIO

Backup:

```powershell
.\scripts\ops\backup-restore.ps1 -Target minio -Action backup
```

Restore:

```powershell
.\scripts\ops\backup-restore.ps1 -Target minio -Action restore -InputFile <minio-backup>
```

Run a restore drill at least once per release. Record backup checksum, restore duration, and application smoke result.

For release or GA evidence, copy `docs/operations/restore-drill-evidence-transcript.template.json` into the release evidence directory, replace placeholders with real target-environment values, and attach only sanitized transcripts or aggregate integrity summaries. Validate the completed transcript before adding it to the GA evidence manifest:

```powershell
.\scripts\ops\validate-evidence-transcript.ps1 -TranscriptPath .\docs\operations\evidence\<release>\restore-drill-evidence-transcript.json -Type restore-drill -MaxAgeDays 14
```

The GA manifest should reference the completed transcript, sanitized restore command transcript, post-restore smoke transcript, and restore integrity summary under the `restore-drill` artifact. Do not store raw dumps, raw customer rows, credentials, kubeconfigs, or backup payloads in the repository.
