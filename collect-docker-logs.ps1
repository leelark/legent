# ================================

# Docker Logs Collector Script

# ================================

Write-Host "Starting Docker Logs Collection..." -ForegroundColor Green

# Create logs directory with timestamp

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$baseFolder = "docker-logs-$timestamp"
New-Item -ItemType Directory -Path $baseFolder | Out-Null

# File for combined logs

$combinedLogFile = "$baseFolder\all-logs.txt"

# Get all containers

$containers = docker ps -a --format "{{.Names}}"

foreach ($container in $containers) {
Write-Host "Collecting logs for $container ..." -ForegroundColor Yellow

```
# Individual container log file
$containerLogFile = "$baseFolder\$container.txt"

# Write header in combined file
"===== $container =====" | Out-File -Append $combinedLogFile

# Get logs (stdout + stderr)
docker logs $container 2>&1 | Tee-Object -FilePath $containerLogFile | Out-File -Append $combinedLogFile

# Add spacing
"`n" | Out-File -Append $combinedLogFile
```

}

Write-Host "Logs collected successfully in folder: $baseFolder" -ForegroundColor Green
