# ================================
# Docker Logs Collector Script
# ================================

Write-Host "Starting Docker Logs Collection..." -ForegroundColor Green

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$baseFolder = "docker-logs-$timestamp"
New-Item -ItemType Directory -Path $baseFolder | Out-Null

$combinedLogFile = Join-Path $baseFolder "all-logs.txt"
$containers = docker ps -a --format "{{.Names}}"

foreach ($container in $containers) {
    Write-Host "Collecting logs for $container ..." -ForegroundColor Yellow

    $containerLogFile = Join-Path $baseFolder "$container.txt"
    "===== $container =====" | Out-File -Append -Encoding utf8 $combinedLogFile

    # Capture both stdout and stderr without PowerShell terminating behavior.
    $logOutput = docker logs $container 2>&1 | Out-String
    $logOutput | Out-File -Encoding utf8 $containerLogFile
    $logOutput | Out-File -Append -Encoding utf8 $combinedLogFile

    "`n" | Out-File -Append -Encoding utf8 $combinedLogFile
}

Write-Host "Logs collected successfully in folder: $baseFolder" -ForegroundColor Green
