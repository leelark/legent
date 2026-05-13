param(
    [int] $TimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$frontendContainerPort = 3000
$defaultFrontendHostPort = 3000
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

function ConvertTo-PortNumber($Value) {
    if ($null -eq $Value) {
        return $null
    }

    $portText = ([string] $Value).Trim()
    if ([string]::IsNullOrWhiteSpace($portText)) {
        return $null
    }

    $port = 0
    if ([int]::TryParse($portText, [ref] $port) -and $port -gt 0 -and $port -le 65535) {
        return $port
    }

    return $null
}

function Get-ConfiguredFrontendHostPort {
    $envPort = ConvertTo-PortNumber $env:FRONTEND_HOST_PORT
    if ($null -ne $envPort) {
        return [pscustomobject] @{
            Port = $envPort
            Source = "FRONTEND_HOST_PORT"
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:FRONTEND_HOST_PORT)) {
        Write-Warning "Ignoring invalid FRONTEND_HOST_PORT value; using default frontend host port $defaultFrontendHostPort"
    }

    return [pscustomobject] @{
        Port = $defaultFrontendHostPort
        Source = "default"
    }
}

function Resolve-FrontendHostPort($Services) {
    $frontendService = $Services | Where-Object { $_.Service -eq "frontend" } | Select-Object -First 1
    if ($null -ne $frontendService) {
        foreach ($publisher in @($frontendService.Publishers)) {
            $targetPort = ConvertTo-PortNumber $publisher.TargetPort
            $publishedPort = ConvertTo-PortNumber $publisher.PublishedPort
            if ($targetPort -eq $frontendContainerPort -and $null -ne $publishedPort) {
                return [pscustomobject] @{
                    Port = $publishedPort
                    Source = "docker compose"
                }
            }
        }

        $ports = [string] $frontendService.Ports
        $publishedPortMatches = [regex]::Matches($ports, "(?<publishedPort>\d+)->$frontendContainerPort/tcp")
        foreach ($publishedPortMatch in $publishedPortMatches) {
            $publishedPort = ConvertTo-PortNumber $publishedPortMatch.Groups["publishedPort"].Value
            if ($null -ne $publishedPort) {
                return [pscustomobject] @{
                    Port = $publishedPort
                    Source = "docker compose ports"
                }
            }
        }
    }

    return Get-ConfiguredFrontendHostPort
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
        $frontendHostPort = Resolve-FrontendHostPort $services
        Write-Host "Checking frontend health on host port $($frontendHostPort.Port) (source: $($frontendHostPort.Source))"
        Test-HttpEndpoint "http://127.0.0.1:$($frontendHostPort.Port)/api/health"
        Test-HttpEndpoint "http://127.0.0.1:8080/api/health"
        Write-Host "Docker Compose health validation passed"
        exit 0
    }

    $lastErrors = $errors
    Start-Sleep -Seconds 5
} while ((Get-Date) -lt $deadline)

throw "Docker Compose health validation failed:`n - $($lastErrors -join "`n - ")"
