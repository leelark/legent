param(
    [int] $TimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$requiredHealthy = @(
    "frontend",
    "gateway",
    "foundation-service",
    "identity-service",
    "audience-service",
    "campaign-service",
    "content-service",
    "delivery-service",
    "tracking-service",
    "automation-service",
    "deliverability-service",
    "platform-service",
    "postgres",
    "redis",
    "kafka",
    "zookeeper",
    "minio",
    "opensearch",
    "clickhouse"
)

function Get-ComposeServices {
    Push-Location $repoRoot
    try {
        $lines = docker compose ps --format json
    } finally {
        Pop-Location
    }
    @($lines | Where-Object { $_ -and $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
}

function Test-HttpEndpoint($Url) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 $Url
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
            throw "Unexpected status $($response.StatusCode)"
        }
    } catch {
        throw "Endpoint failed: $Url - $($_.Exception.Message)"
    }
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$lastErrors = @()

do {
    $services = Get-ComposeServices
    $byService = @{}
    foreach ($service in $services) {
        $byService[$service.Service] = $service
    }

    $errors = @()
    foreach ($serviceName in $requiredHealthy) {
        if (-not $byService.ContainsKey($serviceName)) {
            $errors += "Missing service: $serviceName"
            continue
        }
        $service = $byService[$serviceName]
        if ($service.State -ne "running") {
            $errors += "$serviceName state is $($service.State)"
            continue
        }
        if ($service.Health -and $service.Health -ne "healthy") {
            $errors += "$serviceName health is $($service.Health)"
        }
    }

    if ($errors.Count -eq 0) {
        Test-HttpEndpoint "http://127.0.0.1:3003/api/health"
        Test-HttpEndpoint "http://127.0.0.1:8080/api/health"
        Write-Host "Docker Compose health validation passed"
        exit 0
    }

    $lastErrors = $errors
    Start-Sleep -Seconds 5
} while ((Get-Date) -lt $deadline)

throw "Docker Compose health validation failed:`n - $($lastErrors -join "`n - ")"
