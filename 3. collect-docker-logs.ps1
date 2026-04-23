# ================================
# Docker Logs Collector (CLEAN SIMPLE)
# ================================

Write-Host "Collecting Docker logs..." -ForegroundColor Green

$baseFolder = "docker-logs"

# Reset folder
if (Test-Path $baseFolder) {
    Remove-Item $baseFolder -Recurse -Force
}
New-Item -ItemType Directory -Path $baseFolder | Out-Null

$combinedLogFile = Join-Path $baseFolder "all-logs.txt"

# Get containers
$containers = docker ps -a --format "{{.ID}} {{.Names}}"

foreach ($line in $containers) {
    $parts = $line -split " ", 2
    $containerId = $parts[0]
    $containerName = $parts[1]

    Write-Host "Collecting $containerName ..." -ForegroundColor Yellow

    $containerLogFile = Join-Path $baseFolder "$containerName.txt"

    # Header in combined file
    "===== $containerName =====" | Out-File -Append -Encoding utf8 $combinedLogFile

    try {
        # Direct logs (no parsing, no Out-String)
        docker logs --timestamps --tail 300 $containerId 2>&1 |
        Tee-Object -FilePath $containerLogFile |
        Out-File -Append -Encoding utf8 $combinedLogFile
    }
    catch {
        "Error collecting logs for $containerName" | Out-File -Append -Encoding utf8 $combinedLogFile
    }

    "`n" | Out-File -Append -Encoding utf8 $combinedLogFile
}

Write-Host "Logs saved in folder: $baseFolder" -ForegroundColor Green