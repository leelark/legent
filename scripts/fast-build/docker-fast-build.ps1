#!/usr/bin/env pwsh
# Docker Fast Build - Optimized Docker-only builds using BuildKit
# This script builds Docker images with maximum caching and parallelization

param(
    [switch]$Clean,
    [string]$Service,
    [switch]$NoCache,
    [switch]$RebuildBase
)

$ErrorActionPreference = "Stop"

# Change to project root (scripts/fast-build -> project root)
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptPath)
Set-Location $projectRoot

$startTime = Get-Date

Write-Host "[DOCKER] Fast Build with BuildKit" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan

# Check if shared base image exists (handles shared library changes)
$imageName = "legent-shared-base:latest"
$baseExists = docker images -q $imageName 2>$null
if (-not $baseExists -or $RebuildBase) {
    if ($RebuildBase) {
        Write-Host "[REBUILD] Rebuilding shared base image (shared libraries changed)..." -ForegroundColor Yellow
    } else {
        Write-Host "[WARN] Shared base image not found! Building first..." -ForegroundColor Yellow
    }
    & "$PSScriptRoot\..\cached-builds\build-shared-base.ps1" -Force:$RebuildBase
    if ($LASTEXITCODE -ne 0) {
        exit 1
    }
}

# Enable BuildKit
$env:DOCKER_BUILDKIT = "1"
$env:COMPOSE_DOCKER_CLI_BUILD = "1"
$env:BUILDKIT_INLINE_CACHE = "1"

# Create cache directories if they don't exist
$cacheDir = ".docker/cache"
if (-not (Test-Path $cacheDir)) {
    New-Item -ItemType Directory -Path $cacheDir -Force | Out-Null
}

# Determine services to build
$services = @()
if ($Service) {
    # Normalize service name (frontend is a special case without -service suffix)
    if (-not ($Service -match "-service$" -or $Service -eq "frontend")) {
        $Service = "$Service-service"
    }
    $services = @($Service)
    Write-Host "[BUILD] Building single service: $Service" -ForegroundColor Yellow
} else {
    # All services
    $services = @(
        "foundation-service",
        "identity-service",
        "content-service",
        "audience-service",
        "campaign-service",
        "delivery-service",
        "tracking-service",
        "automation-service",
        "deliverability-service",
        "platform-service",
        "frontend"
    )
    Write-Host "[BUILD] Building all services in parallel batches" -ForegroundColor Yellow
}

# Phase: Maven Build (required before Docker - services need JAR files)
Write-Host "`n[MAVEN] Building services to create JAR files..." -ForegroundColor Yellow

$mvnw = ".\mvnw.cmd"
$mavenWrapperFunctional = $false
if (Test-Path $mvnw) {
    # Test if wrapper is actually functional
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

# Build shared modules first
$sharedModules = @(
    "shared/legent-common",
    "shared/legent-security",
    "shared/legent-kafka",
    "shared/legent-cache",
    "shared/legent-test-support"
)

$sharedList = $sharedModules -join ","

# Build command - skip frontend (it's not a Maven project)
$mavenServices = $services | Where-Object { $_ -ne "frontend" }
if ($mavenServices.Count -gt 0) {
    $serviceList = ($mavenServices | ForEach-Object { "services/$_" }) -join ","
    Write-Host "[MAVEN] Building modules: $serviceList" -ForegroundColor Gray
    & $mvnw install -pl $serviceList -am -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] Maven build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "[OK] Maven build complete" -ForegroundColor Green
} else {
    Write-Host "[INFO] No Maven services to build (frontend only)" -ForegroundColor Gray
}

# Build arguments
$buildArgs = @()
if ($Clean -or $NoCache) {
    $buildArgs += "--no-cache"
    Write-Host "[WARN] Building without cache (clean build)" -ForegroundColor Yellow
} else {
    Write-Host "[INFO] Using layer cache for faster builds" -ForegroundColor Green
}

# Build function with progress
function Build-Service($svc) {
    Write-Host "  [BUILD] Building $svc..." -ForegroundColor Gray -NoNewline
    $svcStart = Get-Date

    # Run docker build using cmd to avoid PowerShell error handling issues
    $exitCode = 0
    $output = cmd /c "docker compose build $svc 2>&1" 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        Write-Host " [FAIL]" -ForegroundColor Red
        return $false
    }

    $svcEnd = Get-Date
    $duration = [math]::Round(($svcEnd - $svcStart).TotalSeconds)
    Write-Host " [OK] (${duration}s)" -ForegroundColor Green
    return $true
}

# Build sequentially (parallel jobs have issues with Docker context)
$successCount = 0
$failCount = 0

foreach ($svc in $services) {
    $result = Build-Service $svc
    if ($result -eq $true) {
        $successCount++
    } else {
        $failCount++
    }
}

# Summary
$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host "`n====================================" -ForegroundColor Cyan
if ($failCount -eq 0) {
    Write-Host "[SUCCESS] All builds successful!" -ForegroundColor Green
} else {
    Write-Host "[WARN] $successCount succeeded, $failCount failed" -ForegroundColor Yellow
}
Write-Host "[TIME] Total time: $($duration.ToString('mm\:ss'))" -ForegroundColor Cyan

if (-not $Clean) {
    Write-Host "`n[TIPS]:" -ForegroundColor Gray
    Write-Host "   - Run with -Clean for fresh builds without cache" -ForegroundColor Gray
    Write-Host "   - Use -Service <name> to build specific service" -ForegroundColor Gray
    Write-Host "   - Use -RebuildBase when shared/ libraries change" -ForegroundColor Gray
    Write-Host "   - BuildKit caches layers automatically between builds" -ForegroundColor Gray
}

exit $failCount
