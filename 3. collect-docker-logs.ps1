# ============================================
# Docker Logs Collector (LAST RUN ONLY)
# - Captures ONLY last container run
# - No truncation (full logs)
# - Clean + grouped output
# ============================================

Write-Host "Collecting LAST RUN Docker logs..." -ForegroundColor Green

$baseFolder = "docker-logs-last-run"

# Reset folder
if (Test-Path $baseFolder) {
    Remove-Item $baseFolder -Recurse -Force
}
New-Item -ItemType Directory -Path $baseFolder | Out-Null

$combinedLogFile = Join-Path $baseFolder "all-logs.txt"

# Get all containers (including stopped)
$containers = docker ps -a --format "{{.ID}} {{.Names}}"

foreach ($line in $containers) {

    $parts = $line -split " ", 2
    $containerId = $parts[0]
    $containerName = $parts[1]

    Write-Host "Collecting $containerName ..." -ForegroundColor Yellow

    $containerLogFile = Join-Path $baseFolder "$containerName.txt"

    "===== $containerName =====" | Out-File -Append -Encoding utf8 $combinedLogFile

    try {
        # Suppress PowerShell NativeCommandError for stderr redirection
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'SilentlyContinue'

        # LAST RUN ONLY → use --since container start time
        $startTime = docker inspect -f "{{.State.StartedAt}}" $containerId

        docker logs --timestamps --since $startTime $containerId 2>&1 |
        Tee-Object -FilePath $containerLogFile |
        Out-File -Append -Encoding utf8 $combinedLogFile

        $ErrorActionPreference = $oldErrorAction

    } catch {
        "Error collecting logs for $containerName" | Out-File -Append -Encoding utf8 $combinedLogFile
    }

    "`n" | Out-File -Append -Encoding utf8 $combinedLogFile
}

Write-Host "Logs saved in folder: $baseFolder" -ForegroundColor Green