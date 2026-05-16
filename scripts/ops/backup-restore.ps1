param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("postgres", "clickhouse", "minio")]
    [string] $Target,

    [Parameter(Mandatory = $true)]
    [ValidateSet("backup", "restore", "drill")]
    [string] $Action,

    [string] $Namespace = "legent",
    [string] $OutputDir,
    [string] $InputFile
)

$ErrorActionPreference = "Stop"

function Require-Command($Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is required"
    }
}

function New-BackupPath($Name, $Extension) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    return Join-Path $OutputDir "$Name-$stamp.$Extension"
}

function New-TempDir($Name) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $path = Join-Path ([System.IO.Path]::GetTempPath()) "$Name-$stamp"
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    return $path
}

function New-Utf8NoBomEncoding {
    return New-Object System.Text.UTF8Encoding($false)
}

function Set-Utf8NoBomText($Path, $Value) {
    [System.IO.File]::WriteAllText($Path, $Value, (New-Utf8NoBomEncoding))
}

function Add-Utf8NoBomLine($Path, $Value) {
    [System.IO.File]::AppendAllText($Path, "$Value$([Environment]::NewLine)", (New-Utf8NoBomEncoding))
}

function Set-Utf8NoBomLines($Path, $Lines) {
    $writer = New-Object System.IO.StreamWriter($Path, $false, (New-Utf8NoBomEncoding))
    try {
        foreach ($line in $Lines) {
            $writer.WriteLine($line)
        }
    } finally {
        $writer.Dispose()
    }
}

function Quote-ClickHouseIdentifier($Name) {
    $tick = [char]96
    return "$tick$($Name.Replace("$tick", "$tick$tick"))$tick"
}

function Quote-ClickHouseString($Value) {
    return "'" + $Value.Replace("\", "\\").Replace("'", "\'") + "'"
}

function Invoke-ClickHouseQuery($Query) {
    kubectl exec -n $Namespace deploy/legent-clickhouse -- clickhouse-client --query $Query
}

function Get-DefaultBackupDir {
    if (-not [string]::IsNullOrWhiteSpace($env:LEGENT_BACKUP_DIR)) {
        return $env:LEGENT_BACKUP_DIR
    }
    if (-not [string]::IsNullOrWhiteSpace($HOME)) {
        return Join-Path $HOME "legent-backups"
    }
    return Join-Path ([System.IO.Path]::GetTempPath()) "legent-backups"
}

function Resolve-WritePath($Path) {
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Test-PathInside($Path, $ParentPath) {
    $trimChars = [char[]]@([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $fullPath = ([System.IO.Path]::GetFullPath($Path)).TrimEnd($trimChars)
    $fullParentPath = ([System.IO.Path]::GetFullPath($ParentPath)).TrimEnd($trimChars)
    return $fullPath.Equals($fullParentPath, [System.StringComparison]::OrdinalIgnoreCase) -or
        $fullPath.StartsWith("$fullParentPath$([System.IO.Path]::DirectorySeparatorChar)", [System.StringComparison]::OrdinalIgnoreCase) -or
        $fullPath.StartsWith("$fullParentPath$([System.IO.Path]::AltDirectorySeparatorChar)", [System.StringComparison]::OrdinalIgnoreCase)
}

function Assert-BackupOutputDir($Path) {
    $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
    $outputPath = Resolve-WritePath $Path
    $ignoredRepoBackupDir = Resolve-WritePath (Join-Path $repoRoot "backups")
    if ((Test-PathInside $outputPath $repoRoot) -and -not (Test-PathInside $outputPath $ignoredRepoBackupDir)) {
        throw "Refusing repo-local backup output: $outputPath. Use -OutputDir outside the repo, or .\backups for ignored local-only output."
    }
}

function Backup-ClickHouse {
    $archivePath = New-BackupPath "clickhouse" "zip"
    $workDir = New-TempDir "legent-clickhouse-backup"
    $schemaPath = Join-Path $workDir "schema.sql"
    $manifestPath = Join-Path $workDir "manifest.tsv"
    $dataDir = Join-Path $workDir "data"
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null

    Set-Utf8NoBomText $schemaPath "-- Legent ClickHouse backup$([Environment]::NewLine)"
    Set-Utf8NoBomText $manifestPath "database`tname`tkind`tfile$([Environment]::NewLine)"

    $databases = Invoke-ClickHouseQuery "SELECT name FROM system.databases WHERE name NOT IN ('system', 'INFORMATION_SCHEMA', 'information_schema') ORDER BY name FORMAT TabSeparatedRaw"
    foreach ($db in $databases) {
        if ([string]::IsNullOrWhiteSpace($db)) { continue }
        $dbName = $db.Trim()
        $quotedDb = Quote-ClickHouseIdentifier $dbName
        Add-Utf8NoBomLine $schemaPath "CREATE DATABASE IF NOT EXISTS $quotedDb;"

        $dbLiteral = Quote-ClickHouseString $dbName
        $tables = Invoke-ClickHouseQuery "SELECT name FROM system.tables WHERE database = $dbLiteral ORDER BY name FORMAT TabSeparatedRaw"
        foreach ($table in $tables) {
            if ([string]::IsNullOrWhiteSpace($table)) { continue }
            $tableName = $table.Trim()
            $quotedTable = Quote-ClickHouseIdentifier $tableName
            $tableLiteral = Quote-ClickHouseString $tableName
            $createQuery = (Invoke-ClickHouseQuery "SELECT create_table_query FROM system.tables WHERE database = $dbLiteral AND name = $tableLiteral FORMAT TabSeparatedRaw") -join "`n"
            if ([string]::IsNullOrWhiteSpace($createQuery)) {
                throw "Could not read ClickHouse DDL for $dbName.$tableName"
            }

            Add-Utf8NoBomLine $schemaPath "$createQuery;"

            $isMaterializedView = $createQuery.TrimStart().StartsWith("CREATE MATERIALIZED VIEW", [System.StringComparison]::OrdinalIgnoreCase)
            $isView = $createQuery.TrimStart().StartsWith("CREATE VIEW", [System.StringComparison]::OrdinalIgnoreCase)
            if ($isMaterializedView -or $isView) {
                Add-Utf8NoBomLine $manifestPath "$dbName`t$tableName`tschema_only`t-"
                continue
            }

            $safeFile = (($dbName + "." + $tableName) -replace "[^A-Za-z0-9_.-]", "_") + ".tsv"
            $dataPath = Join-Path $dataDir $safeFile
            Set-Utf8NoBomLines $dataPath (Invoke-ClickHouseQuery "SELECT * FROM $quotedDb.$quotedTable FORMAT TSVWithNamesAndTypes")
            Add-Utf8NoBomLine $manifestPath "$dbName`t$tableName`ttable_data`tdata/$safeFile"
        }
    }

    Compress-Archive -Path (Join-Path $workDir "*") -DestinationPath $archivePath -Force
    Remove-Item -LiteralPath $workDir -Recurse -Force
    Write-Host "ClickHouse backup written to $archivePath"
}

function Expand-ClickHouseArchive($ArchivePath) {
    if (-not (Test-Path -LiteralPath $ArchivePath)) {
        throw "InputFile does not exist: $ArchivePath"
    }
    $workDir = New-TempDir "legent-clickhouse-restore"
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $workDir -Force
    $schemaPath = Join-Path $workDir "schema.sql"
    $manifestPath = Join-Path $workDir "manifest.tsv"
    if (-not (Test-Path -LiteralPath $schemaPath)) { throw "schema.sql missing from ClickHouse backup" }
    if (-not (Test-Path -LiteralPath $manifestPath)) { throw "manifest.tsv missing from ClickHouse backup" }
    return $workDir
}

function Restore-ClickHouse($ArchivePath) {
    $workDir = Expand-ClickHouseArchive $ArchivePath
    try {
        Get-Content -LiteralPath (Join-Path $workDir "schema.sql") -Raw |
            kubectl exec -i -n $Namespace deploy/legent-clickhouse -- clickhouse-client --multiquery

        $manifestLines = Get-Content -LiteralPath (Join-Path $workDir "manifest.tsv") | Select-Object -Skip 1
        foreach ($line in $manifestLines) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            $parts = $line.Split("`t")
            if ($parts.Count -lt 4 -or $parts[2] -ne "table_data") { continue }
            $dbName = $parts[0]
            $tableName = $parts[1]
            $relativePath = $parts[3]
            $dataPath = Join-Path $workDir $relativePath
            if (-not (Test-Path -LiteralPath $dataPath)) {
                throw "Data file missing from ClickHouse backup: $relativePath"
            }
            $quotedDb = Quote-ClickHouseIdentifier $dbName
            $quotedTable = Quote-ClickHouseIdentifier $tableName
            Get-Content -LiteralPath $dataPath -Raw |
                kubectl exec -i -n $Namespace deploy/legent-clickhouse -- clickhouse-client --query "INSERT INTO $quotedDb.$quotedTable FORMAT TSVWithNamesAndTypes"
        }
        Write-Host "ClickHouse restore completed from $ArchivePath"
    } finally {
        Remove-Item -LiteralPath $workDir -Recurse -Force
    }
}

function Drill-ClickHouseBackup($ArchivePath) {
    $workDir = Expand-ClickHouseArchive $ArchivePath
    try {
        $manifestLines = Get-Content -LiteralPath (Join-Path $workDir "manifest.tsv") | Select-Object -Skip 1
        $dataFiles = 0
        foreach ($line in $manifestLines) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            $parts = $line.Split("`t")
            if ($parts.Count -lt 4) { throw "Invalid manifest row: $line" }
            if ($parts[2] -eq "table_data") {
                $dataFiles += 1
                $dataPath = Join-Path $workDir $parts[3]
                if (-not (Test-Path -LiteralPath $dataPath)) {
                    throw "Data file missing from ClickHouse backup: $($parts[3])"
                }
            }
        }
        Invoke-ClickHouseQuery "SELECT 1 FORMAT TabSeparatedRaw" | Out-Null
        Write-Host "ClickHouse backup drill passed: schema present, $dataFiles data file(s) present, target ClickHouse reachable"
    } finally {
        Remove-Item -LiteralPath $workDir -Recurse -Force
    }
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Get-DefaultBackupDir
}

if ($Action -eq "backup") {
    Assert-BackupOutputDir $OutputDir
}

Require-Command kubectl

switch ("$Target/$Action") {
    "postgres/backup" {
        $path = New-BackupPath "postgres" "sql"
        kubectl exec -n $Namespace deploy/legent-postgres -- pg_dumpall -U legent | Set-Content -LiteralPath $path -Encoding UTF8
        Write-Host "Postgres backup written to $path"
    }
    "postgres/restore" {
        if (-not $InputFile) { throw "InputFile is required for restore" }
        Get-Content -LiteralPath $InputFile -Raw | kubectl exec -i -n $Namespace deploy/legent-postgres -- psql -U legent
    }
    "clickhouse/backup" {
        Backup-ClickHouse
    }
    "clickhouse/restore" {
        if (-not $InputFile) { throw "InputFile is required for restore" }
        Restore-ClickHouse $InputFile
    }
    "clickhouse/drill" {
        if (-not $InputFile) { throw "InputFile is required for drill" }
        Drill-ClickHouseBackup $InputFile
    }
    "minio/backup" {
        $path = New-BackupPath "minio" "tar"
        kubectl exec -n $Namespace deploy/legent-minio -- tar -C /data -cf - . | Set-Content -LiteralPath $path -Encoding Byte
        Write-Host "MinIO backup written to $path"
    }
    "minio/restore" {
        if (-not $InputFile) { throw "InputFile is required for restore" }
        Get-Content -LiteralPath $InputFile -Encoding Byte -Raw | kubectl exec -i -n $Namespace deploy/legent-minio -- tar -C /data -xf -
    }
}
