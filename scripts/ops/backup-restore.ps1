param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("postgres", "clickhouse", "minio")]
    [string] $Target,

    [Parameter(Mandatory = $true)]
    [ValidateSet("backup", "restore")]
    [string] $Action,

    [string] $Namespace = "legent",
    [string] $OutputDir = ".\backups",
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
        $path = New-BackupPath "clickhouse" "sql"
        kubectl exec -n $Namespace deploy/legent-clickhouse -- clickhouse-client --query "SHOW DATABASES" | Set-Content -LiteralPath $path -Encoding UTF8
        Write-Host "ClickHouse database inventory written to $path"
    }
    "clickhouse/restore" {
        if (-not $InputFile) { throw "InputFile is required for restore" }
        Get-Content -LiteralPath $InputFile -Raw | kubectl exec -i -n $Namespace deploy/legent-clickhouse -- clickhouse-client --multiquery
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
