#!/usr/bin/env pwsh
# Legent: All-in-One Build & Deploy Script
# Builds Maven, Docker images, starts containers, and monitors health
# Usage: .\scripts\fast-build\all-in-one.ps1 [-Service <name>] [-Clean] [-SkipBuild] [-LogFile <path>]

param(
    [string]$Service,
    [switch]$Clean,
    [switch]$SkipBuild,
    [switch]$NoCache,
    [int]$HealthCheckInterval = 5,
    [int]$MaxHealthWait = 600,
    [string]$LogFile = "$PSScriptRoot\..\..\all-in-one.log"
)

$ErrorActionPreference = "Stop"
$startTime = Get-Date

# Initialize log file
echo "=== Legent All-in-One Log Started: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" > $LogFile
echo "" >> $LogFile

# Colors
$Cyan = "Cyan"
$Green = "Green"
$Yellow = "Yellow"
$Red = "Red"
$Gray = "Gray"

function Write-Status($Message, $Color = $Gray) {
    Write-Host $Message -ForegroundColor $Color
    # Also log to file
    $timestamp = Get-Date -Format 'HH:mm:ss'
    echo "[$timestamp] $Message" >> $LogFile
}

function Write-ErrorLog($Message) {
    echo "[ERROR] $(Get-Date -Format 'HH:mm:ss') - $Message" >> $LogFile
    echo "" >> $LogFile
}

Write-Status "================================================" $Cyan
Write-Status "    LEGENT ALL-IN-ONE BUILD & DEPLOY" $Cyan
Write-Status "================================================" $Cyan
Write-Status "Log File: $LogFile" $Gray
Write-Status ""

# Phase 1: MAVEN BUILD
if (-not $SkipBuild) {
    Write-Status "[PHASE 1] Maven Build" $Yellow
    
    $mvn = if (Test-Path ".\mvnw.cmd") { 
        try { & .\mvnw.cmd --version 2>$null; if ($LASTEXITCODE -eq 0) { ".\mvnw.cmd" } else { "mvn" } }
        catch { "mvn" }
    } else { "mvn" }
    
    Write-Status "  Using: $mvn" $Gray
    
    $mvnArgs = @("install", "-DskipTests")
    if ($Clean) { $mvnArgs = @("clean") + $mvnArgs }
    if ($Service) {
        $svc = $Service -replace "-service$", ""
        $mvnArgs += @("-pl", "services/$svc-service", "-am")
        Write-Status "  Target: $svc-service" $Yellow
    } else {
        Write-Status "  Target: All Services" $Yellow
    }
    
    echo "[MAVEN COMMAND] $mvn $($mvnArgs -join ' ')" >> $LogFile
    $mvnStart = Get-Date
    & $mvn @mvnArgs 2>&1 | Tee-Object -FilePath $LogFile -Append
    $mvnExit = $LASTEXITCODE
    
    if ($mvnExit -ne 0) {
        Write-Status "[FAIL] Maven build failed! Code: $mvnExit" $Red
        Write-ErrorLog "Maven build failed with exit code $mvnExit"
        exit 1
    }
    $mvnTime = (Get-Date) - $mvnStart
    Write-Status "[OK] Maven build completed in $($mvnTime.ToString('mm\:ss'))" $Green
} else {
    Write-Status "[PHASE 1] Maven Build [SKIP]" $Yellow
}

# Phase 2: DOCKER BUILD
if (-not $SkipBuild) {
    Write-Status "[PHASE 2] Docker Build" $Yellow
    
    $env:DOCKER_BUILDKIT = "1"
    $env:COMPOSE_DOCKER_CLI_BUILD = "1"
    
    $dockerSvc = if ($Service) { 
        if ($Service -eq "frontend") { "frontend" } else { "$Service-service" }
    } else { "" }
    
    $dockerStart = Get-Date
    if ($dockerSvc) {
        Write-Status "  Building: $dockerSvc" $Gray
        echo "[DOCKER] Building: $dockerSvc" >> $LogFile
        cmd /c "docker compose build $dockerSvc 2>&1" | Tee-Object -FilePath $LogFile -Append
    } else {
        Write-Status "  Building: All Services" $Gray
        echo "[DOCKER] Building all services" >> $LogFile
        cmd /c "docker compose build 2>&1" | Tee-Object -FilePath $LogFile -Append
    }
    $dockerExit = $LASTEXITCODE
    
    if ($dockerExit -ne 0) {
        Write-Status "[FAIL] Docker build failed! Code: $dockerExit" $Red
        Write-ErrorLog "Docker build failed with exit code $dockerExit"
        exit 1
    }
    $dockerTime = (Get-Date) - $dockerStart
    Write-Status "[OK] Docker build completed in $($dockerTime.ToString('mm\:ss'))" $Green
} else {
    Write-Status "[PHASE 2] Docker Build [SKIP]" $Yellow
}

# Phase 3: DOCKER COMPOSE UP
Write-Status "[PHASE 3] Docker Compose Up" $Yellow

$dockerService = if ($Service) { 
    if ($Service -eq "frontend") { "frontend" } else { "$Service-service" }
} else { "" }

echo "[DEPLOY] Starting containers..." >> $LogFile
$deployStart = Get-Date

if ($dockerService) {
    Write-Status "  Starting: $dockerService" $Gray
    echo "[DEPLOY] Service: $dockerService" >> $LogFile
    cmd /c "docker compose up -d $dockerService 2>&1" | Out-Null
} else {
    Write-Status "  Starting: All Services + Infrastructure" $Gray
    echo "[DEPLOY] All services" >> $LogFile
    cmd /c "docker compose up -d 2>&1" | Out-Null
}

$deployExit = $LASTEXITCODE
if ($deployExit -ne 0) {
    Write-Status "[FAIL] Docker compose up failed! Code: $deployExit" $Red
    Write-ErrorLog "Docker compose up failed with exit code $deployExit"
    exit 1
}
echo "[DEPLOY] Docker compose up completed" >> $LogFile
$deployTime = (Get-Date) - $deployStart
Write-Status "[OK] Containers started in $($deployTime.ToString('mm\:ss'))" $Green
echo "[DEPLOY] Completed in $($deployTime.ToString('mm\:ss'))" >> $LogFile

# Show initial status
Write-Status "" $Gray
Write-Status "Initial Container Status:" $Gray
$initialStatus = docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Health}}" 2>&1 | Out-String
Write-Status $initialStatus $Gray
echo "[STATUS] Initial status:" >> $LogFile
echo $initialStatus >> $LogFile

# Phase 4: Health Check with Live Status
Write-Status "[PHASE 4] Health Check Monitoring" $Yellow
echo "" >> $LogFile
echo "[HEALTH CHECK] Started at $(Get-Date)" >> $LogFile

$healthStart = Get-Date
$allHealthy = $false
$serviceCount = 0
$lastLogTime = Get-Date

# Get list of services to monitor
$targetServices = if ($Service) { 
    @($Service) 
} else { 
    @("foundation", "identity", "content", "audience", "campaign", "delivery", "tracking", "automation", "deliverability", "platform", "frontend")
}

Write-Status "  Monitoring $($targetServices.Count) services..." $Gray

while (-not $allHealthy) {
    $elapsed = [math]::Round(((Get-Date) - $healthStart).TotalSeconds)
    
    if ($elapsed -gt $MaxHealthWait) {
        Write-Status "[TIMEOUT] Health check exceeded ${MaxHealthWait}s" $Red
        Write-ErrorLog "Health check timeout after $MaxHealthWait seconds"
        break
    }
    
    $ps = docker compose ps --format "{{.Name}}|{{.Service}}|{{.Status}}|{{.Health}}" 2>$null
    $allHealthy = $true
    $statusDisplay = @()
    $healthyCount = 0
    
    foreach ($line in $ps) {
        if ($line -match "^(.+)\|(.+)\|(.+)\|(.+)$") {
            $name = $Matches[1]
            $svc = $Matches[2]
            $status = $Matches[3]
            $health = $Matches[4]
            
            # Check if this is one of our target services
            $isTarget = $false
            foreach ($target in $targetServices) {
                if ($svc -eq $target -or $svc -eq "$target-service" -or ($target -eq "frontend" -and $svc -eq "frontend")) {
                    $isTarget = $true
                    break
                }
            }
            if (-not $isTarget) { continue }
            
            $icon = if ($health -eq "healthy") { "[+]" } elseif ($health -eq "starting") { "[~]" } else { "[X]" }
            $color = if ($health -eq "healthy") { $Green } elseif ($health -eq "starting") { $Yellow } else { $Red }
            
            if ($health -eq "healthy") { $healthyCount++ }
            if ($health -ne "healthy") { $allHealthy = $false }
            
            $statusDisplay += @{ Name = $name; Icon = $icon; Health = $health; Color = $color }
        }
    }
    
    # Display status
    Write-Status "" $Gray
    Write-Status "  Elapsed: ${elapsed}s | Healthy: $healthyCount/$($targetServices.Count)" $Gray
    foreach ($s in $statusDisplay) {
        Write-Status "    $($s.Icon) $($s.Name): $($s.Health)" $s.Color
    }
    
    # Log status every 30 seconds
    if (((Get-Date) - $lastLogTime).TotalSeconds -ge 30) {
        echo "[HEALTH ${elapsed}s] Healthy: $healthyCount/$($targetServices.Count)" >> $LogFile
        $lastLogTime = Get-Date
    }
    
    if (-not $allHealthy) {
        Start-Sleep -Seconds $HealthCheckInterval
    }
}

$healthTime = (Get-Date) - $healthStart
echo "[HEALTH CHECK] Completed in $($healthTime.ToString('mm\:ss'))" >> $LogFile

# Final Status
Write-Status ""
Write-Status ""
Write-Status "================================================" $Cyan
$endTime = Get-Date
$duration = $endTime - $startTime

if ($allHealthy) {
    Write-Status "[SUCCESS] ALL SERVICES HEALTHY!" $Green
    Write-Status "Total time: $($duration.ToString('mm\:ss'))" $Green
    Write-Status ""
    Write-Status "Access Points:" $Yellow
    Write-Status "  - API Gateway: http://localhost:8080" $Gray
    Write-Status "  - Frontend:    http://localhost:3000" $Gray
    Write-Status "  - MailHog:     http://localhost:8025" $Gray
    Write-Status "  - Kafka UI:    http://localhost:8091" $Gray
    Write-Status "  - MinIO:       http://localhost:9001" $Gray
    exit 0
} else {
    Write-Status "[WARNING] Some services not healthy yet" $Yellow
    Write-Status "Run 'docker compose ps' to check status" $Gray
    Write-Status "Run 'docker compose logs <service>' for details" $Gray
    exit 1
}
