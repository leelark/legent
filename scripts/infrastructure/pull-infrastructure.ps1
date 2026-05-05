#!/usr/bin/env pwsh
# Pull infrastructure images once and cache them locally
# Run this once to pre-download all infrastructure images
# After this, 'docker compose up' won't need to download anything

param(
    [switch]$Force,
    [switch]$OnlyMissing
)

$ErrorActionPreference = "Stop"

# Change to project root (scripts/infrastructure -> project root)
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptPath)
Set-Location $projectRoot

$images = @(
    @{ Name = "PostgreSQL"; Image = "postgres:15-alpine"; Size = "~100MB" }
    @{ Name = "Redis"; Image = "redis:7-alpine"; Size = "~30MB" }
    @{ Name = "Zookeeper"; Image = "confluentinc/cp-zookeeper:7.5.0"; Size = "~400MB" }
    @{ Name = "Kafka"; Image = "confluentinc/cp-kafka:7.5.0"; Size = "~500MB" }
    @{ Name = "OpenSearch"; Image = "opensearchproject/opensearch:2.11.0"; Size = "~1.2GB" }
    @{ Name = "ClickHouse"; Image = "clickhouse/clickhouse-server:23.8-alpine"; Size = "~300MB" }
    @{ Name = "MinIO"; Image = "minio/minio"; Size = "~200MB" }
    @{ Name = "MailHog"; Image = "mailhog/mailhog"; Size = "~20MB" }
    @{ Name = "Kafka UI"; Image = "kafbat/kafka-ui:latest"; Size = "~150MB" }
    @{ Name = "Nginx"; Image = "nginx:1.27-alpine"; Size = "~20MB" }
)

Write-Host "🐳 Infrastructure Image Puller" -ForegroundColor Cyan
Write-Host "==============================" -ForegroundColor Cyan
Write-Host ""

if ($Force) {
    Write-Host "⚠️ Force mode: Will re-download all images" -ForegroundColor Yellow
}

$totalSize = 0
$pulledCount = 0
$skippedCount = 0
$startTime = Get-Date

foreach ($img in $images) {
    $imageName = $img.Image
    $displayName = $img.Name

    Write-Host "📦 $displayName ($imageName)" -ForegroundColor White -NoNewline

    # Check if image exists locally
    $exists = docker images -q $imageName 2>$null

    if ($exists -and -not $Force) {
        if ($OnlyMissing) {
            Write-Host " ✓ (already cached)" -ForegroundColor Green
            $skippedCount++
            continue
        }
        # Try to get image size
        $inspect = docker inspect $imageName | ConvertFrom-Json
        $size = [math]::Round($inspect[0].Size / 1MB, 1)
        Write-Host " ✓ (cached: ${size}MB)" -ForegroundColor Green
        $skippedCount++
    } else {
        Write-Host " ⬇️ Downloading..." -ForegroundColor Yellow
        $pullStart = Get-Date

        try {
            docker pull $imageName 2>&1 | ForEach-Object {
                # Show progress dots
                if ($_ -match "^\s+\w+:\s+\w+") {
                    Write-Host "." -ForegroundColor Gray -NoNewline
                }
            }
            Write-Host ""

            if ($LASTEXITCODE -eq 0) {
                $pullEnd = Get-Date
                $duration = [math]::Round(($pullEnd - $pullStart).TotalSeconds)
                Write-Host "   ✅ Downloaded in ${duration}s" -ForegroundColor Green
                $pulledCount++
            } else {
                Write-Host "   ❌ Failed to download" -ForegroundColor Red
            }
        } catch {
            Write-Host "   ❌ Error: $_" -ForegroundColor Red
        }
    }
}

$endTime = Get-Date
$totalDuration = $endTime - $startTime

Write-Host ""
Write-Host "==============================" -ForegroundColor Cyan
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "  Downloaded: $pulledCount images" -ForegroundColor White
Write-Host "  Already cached: $skippedCount images" -ForegroundColor White
Write-Host "  Total time: $($totalDuration.ToString('mm\:ss'))" -ForegroundColor White

if ($pulledCount -gt 0) {
    Write-Host ""
    Write-Host "💡 Next time you run 'docker compose up', it will start instantly!" -ForegroundColor Green
}

Write-Host ""
Write-Host "📚 To manage images:" -ForegroundColor Gray
Write-Host "  View all: docker images" -ForegroundColor Gray
Write-Host "  View space: docker system df" -ForegroundColor Gray
Write-Host "  Clean unused: docker image prune -a" -ForegroundColor Gray
