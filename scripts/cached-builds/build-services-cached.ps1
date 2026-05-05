#!/usr/bin/env pwsh
# Build services using cached shared base image
# Much faster - only rebuilds service code, not shared dependencies

param(
    [string]$Service,
    [switch]$All,
    [switch]$NoCache,
    [switch]$RebuildBaseOnly
)

$ErrorActionPreference = "Stop"

# Change to project root (scripts/cached-builds -> project root)
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptPath)
Set-Location $projectRoot

$startTime = Get-Date

$imageName = "legent-shared-base:latest"

Write-Host "[CACHED] Legent Cached Service Builder" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan

# Phase 1: Maven compilation (if not just rebuilding base)
if (-not ($PSBoundParameters.ContainsKey('RebuildBaseOnly') -and $RebuildBaseOnly)) {
    Write-Host "`n[MAVEN] Phase 1: Compiling with Maven..." -ForegroundColor Yellow
    
    # Build shared libraries first
    $sharedModules = @(
        "shared/legent-common",
        "shared/legent-security", 
        "shared/legent-kafka",
        "shared/legent-cache"
    )
    
    $mvnw = ".\mvnw.cmd"
    $mavenWrapperFunctional = $false
    if (Test-Path $mvnw) {
        try {
            $testOutput = & $mvnw --version 2>&1
            if ($LASTEXITCODE -eq 0) {
                $mavenWrapperFunctional = $true
            }
        } catch {
            $mavenWrapperFunctional = $false
        }
    }
    if (-not $mavenWrapperFunctional) {
        $mvnw = "mvn"
        Write-Host "[INFO] Using system Maven (wrapper not functional)" -ForegroundColor Gray
    }
    
    Write-Host "  [BUILD] Building shared libraries..." -ForegroundColor Gray
    & $mvnw install -pl ($sharedModules -join ",") -am -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] Shared library compilation failed!" -ForegroundColor Red
        exit 1
    }
    
    # Build services
    if ($All) {
        Write-Host "  [BUILD] Building all services..." -ForegroundColor Gray
        $serviceList = ($services -join ",")
        & $mvnw install -pl $serviceList -am -T 1C
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[FAIL] Service compilation failed!" -ForegroundColor Red
            exit 1
        }
    } elseif ($Service) {
        Write-Host "  [BUILD] Building $Service..." -ForegroundColor Gray
        & $mvnw install -pl services/$Service -am
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[FAIL] Service compilation failed!" -ForegroundColor Red
            exit 1
        }
    }
    
    Write-Host "[OK] Maven compilation complete" -ForegroundColor Green
}

# Check if base image exists
$baseExists = docker images -q $imageName 2>$null
if (-not $baseExists) {
    Write-Host "[WARN] Base image not found! Building first..." -ForegroundColor Yellow
    & "$PSScriptRoot\..\cached-builds\build-shared-base.ps1"
    if ($LASTEXITCODE -ne 0) {
        exit 1
    }
}

# Determine which services to build
$services = @()
if ($Service) {
    # Normalize service name
    if (-not ($Service -match "-service$")) {
        $Service = "$Service-service"
    }
    $services = @($Service)
    Write-Host "[BUILD] Building single service (cached): $Service" -ForegroundColor Yellow
} elseif ($All) {
    $services = @(
        "foundation-service"
        "identity-service"
        "content-service"
        "audience-service"
        "campaign-service"
        "delivery-service"
        "tracking-service"
        "automation-service"
        "deliverability-service"
        "platform-service"
    )
    Write-Host "[BUILD] Building all services with shared cache" -ForegroundColor Yellow
} else {
    Write-Host "[INFO] Usage: .\scripts\cached-builds\build-services-cached.ps1 -Service <name> | -All" -ForegroundColor Yellow
    Write-Host "   Examples:" -ForegroundColor Gray
    Write-Host "     .\scripts\cached-builds\build-services-cached.ps1 -Service campaign" -ForegroundColor Gray
    Write-Host "     .\scripts\cached-builds\build-services-cached.ps1 -All" -ForegroundColor Gray
    exit 1
}

# Build arguments
$cacheArg = if ($NoCache) { "--no-cache" } else { "" }

# Build function
function Build-CachedService($svcName) {
    $svcStart = Get-Date
    Write-Host "  [BUILD] Building $svcName..." -ForegroundColor Gray -NoNewline

    $dockerfile = "services/$svcName/Dockerfile.cached"
    if (-not (Test-Path $dockerfile)) {
        Write-Host " [WARN] (no cached Dockerfile, using standard)" -ForegroundColor Yellow
        docker compose build $svcName $cacheArg 2>&1 | Out-Null
    } else {
        # Use cached build
        docker build -f $dockerfile -t "legent-$svcName`:cached" $cacheArg . 2>&1 | Out-Null
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Host " [FAIL]" -ForegroundColor Red
        return $false
    }

    $svcEnd = Get-Date
    $duration = [math]::Round(($svcEnd - $svcStart).TotalSeconds)
    Write-Host " [OK] (${duration}s)" -ForegroundColor Green
    return $true
}

# Build services
$successCount = 0
$failCount = 0

foreach ($svc in $services) {
    $result = Build-CachedService $svc
    if ($result) {
        $successCount++
    } else {
        $failCount++
    }
}

# Summary
$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host "`n=================================" -ForegroundColor Cyan
if ($failCount -eq 0) {
    Write-Host "[SUCCESS] All services built successfully!" -ForegroundColor Green
} else {
    Write-Host "[WARN] $successCount succeeded, $failCount failed" -ForegroundColor Yellow
}
Write-Host "[TIME] Total time: $($duration.ToString('mm\:ss'))" -ForegroundColor Cyan

if (-not $NoCache) {
    Write-Host "`n[BENEFITS]:" -ForegroundColor Gray
    Write-Host "   - Maven compilation included (shared + services)" -ForegroundColor Gray
    Write-Host "   - Shared libraries cached in base image" -ForegroundColor Gray
    Write-Host "   - Use -RebuildBaseOnly to skip Maven compilation" -ForegroundColor Gray
}

exit $failCount
