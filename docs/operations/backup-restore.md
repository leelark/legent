# Backup And Restore

All backup commands are environment driven. Do not place credentials in scripts or Git. Provide credentials through Kubernetes secrets, the current shell, or the cluster auth context.

## Postgres

Backup:

```powershell
.\scripts\ops\backup-restore.ps1 -Target postgres -Action backup -OutputDir .\backups
```

Restore:

```powershell
.\scripts\ops\backup-restore.ps1 -Target postgres -Action restore -InputFile .\backups\postgres.sql
```

## ClickHouse

Backup:

```powershell
.\scripts\ops\backup-restore.ps1 -Target clickhouse -Action backup -OutputDir .\backups
```

Restore:

```powershell
.\scripts\ops\backup-restore.ps1 -Target clickhouse -Action restore -InputFile .\backups\clickhouse.sql
```

## MinIO

Backup:

```powershell
.\scripts\ops\backup-restore.ps1 -Target minio -Action backup -OutputDir .\backups
```

Restore:

```powershell
.\scripts\ops\backup-restore.ps1 -Target minio -Action restore -InputFile .\backups\minio.tar
```

Run a restore drill at least once per release. Record backup checksum, restore duration, and application smoke result.
