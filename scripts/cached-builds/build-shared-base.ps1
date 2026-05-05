#!/usr/bin/env pwsh
# Build and cache the shared libraries base image
# Run this once initially and whenever shared libraries change

param(
    [switch]$Force,
    [switch]$Push
)

$ErrorActionPreference = "Stop"

# Change to project root (scripts/cached-builds -> project root)
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptPath)
Set-Location $projectRoot

$imageName = "legent-shared-base"
$imageTag = "latest"
$fullImageName = "$imageName`:$imageTag"

Write-Host "* Legent Shared Base Image Builder" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan

# Check if image exists
$imageExists = docker images -q $fullImageName 2>$null

if ($imageExists -and -not $Force) {
    Write-Host "! Base image already exists. Use -Force to rebuild." -ForegroundColor Yellow
    Write-Host "   Current image ID: $imageExists" -ForegroundColor Gray

    # Show image age
    $inspect = docker inspect $fullImageName | ConvertFrom-Json
    $created = $inspect.Created
    Write-Host "   Created: $created" -ForegroundColor Gray
    Write-Host "`n* Tip: Rebuild with -Force when shared libraries change" -ForegroundColor Cyan
    exit 0
}

if ($Force) {
    Write-Host "* Force rebuilding base image..." -ForegroundColor Yellow
    docker rmi -f $fullImageName 2>$null | Out-Null
}

# Build the base image
Write-Host "* Building shared libraries base image..." -ForegroundColor Yellow
Write-Host "   This includes: legent-common, legent-security, legent-kafka, legent-cache" -ForegroundColor Gray

$buildStart = Get-Date

docker build -f shared/Dockerfile -t $fullImageName .

if ($LASTEXITCODE -ne 0) {
    Write-Host "X Base image build failed!" -ForegroundColor Red
    exit 1
}

$buildEnd = Get-Date
$duration = $buildEnd - $buildStart

Write-Host "+ Base image built successfully!" -ForegroundColor Green
Write-Host "   Image: $fullImageName" -ForegroundColor Gray
Write-Host "   Time: $($duration.ToString('mm\:ss'))" -ForegroundColor Gray
Write-Host "   Size: $(docker images --format '{{.Size}}' $fullImageName)" -ForegroundColor Gray

if ($Push) {
    Write-Host "`n* Pushing to registry..." -ForegroundColor Yellow
    docker push $fullImageName
}

Write-Host "`n* Next steps:" -ForegroundColor Cyan
Write-Host "   • Build services using cached base: .\scripts\cached-builds\build-services-cached.ps1" -ForegroundColor White
Write-Host "   • Or use docker compose with: scripts\cached-builds\docker-compose.cached.yml" -ForegroundColor White
Write-Host "   • View image: docker images $imageName" -ForegroundColor Gray
