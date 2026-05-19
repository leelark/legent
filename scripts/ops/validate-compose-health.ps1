param(
    [switch]$AllowNotRunning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

$output = docker compose ps --format json 2>$null
if ($LASTEXITCODE -ne 0) {
    if ($AllowNotRunning) {
        Write-Warning "docker compose ps failed, but AllowNotRunning was set."
        exit 0
    }
    Fail "docker compose ps failed."
}

if (-not $output) {
    if ($AllowNotRunning) {
        Write-Warning "No compose services are running."
        exit 0
    }
    Fail "No compose services are running."
}

$rows = @()
foreach ($line in @($output)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $rows += ($line | ConvertFrom-Json)
}

$bad = @()
foreach ($row in $rows) {
    $state = [string]$row.State
    $health = ""
    if ($row.PSObject.Properties.Name -contains "Health") { $health = [string]$row.Health }
    if ($state -notmatch "running" -or ($health -and $health -notmatch "healthy|starting")) {
        $bad += "$($row.Service): state=$state health=$health"
    }
}

if ($bad.Count -gt 0) {
    $bad | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Compose health validation passed for $($rows.Count) services."
