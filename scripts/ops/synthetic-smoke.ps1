param(
    [string] $BaseUrl = $(if ($env:LEGENT_SMOKE_BASE_URL) { $env:LEGENT_SMOKE_BASE_URL } else { "http://localhost:8080" }),
    [string] $TenantA = "synthetic-tenant-a",
    [string] $TenantB = "synthetic-tenant-b",
    [switch] $SkipTenantIsolation
)

$ErrorActionPreference = "Stop"

function Invoke-SmokeRequest {
    param(
        [string] $Name,
        [string] $Method,
        [string] $Path,
        [hashtable] $Headers = @{},
        [object] $Body = $null,
        [int[]] $ExpectedStatus
    )

    $uri = "$($BaseUrl.TrimEnd('/'))$Path"
    $started = Get-Date
    try {
        $params = @{
            Method = $Method
            Uri = $uri
            Headers = $Headers
            TimeoutSec = 15
            UseBasicParsing = $true
        }
        if ($null -ne $Body) {
            $params["Body"] = ($Body | ConvertTo-Json -Depth 8)
            $params["ContentType"] = "application/json"
        }
        $response = Invoke-WebRequest @params
        $status = [int] $response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int] $_.Exception.Response.StatusCode
        } else {
            throw
        }
    }

    $elapsedMs = [math]::Round(((Get-Date) - $started).TotalMilliseconds)
    if ($ExpectedStatus -notcontains $status) {
        throw "$Name failed: HTTP $status from $Method $Path, expected $($ExpectedStatus -join ', ')"
    }
    Write-Host "$Name passed: HTTP $status in ${elapsedMs}ms"
}

Invoke-SmokeRequest `
    -Name "Foundation health" `
    -Method GET `
    -Path "/api/v1/health" `
    -ExpectedStatus @(200, 204)

Invoke-SmokeRequest `
    -Name "Protected API rejects unauthenticated access" `
    -Method GET `
    -Path "/api/v1/campaigns" `
    -Headers @{ "X-Tenant-Id" = $TenantA; "X-Workspace-Id" = "workspace-a" } `
    -ExpectedStatus @(401, 403)

Invoke-SmokeRequest `
    -Name "Unsafe cross-site write rejected" `
    -Method POST `
    -Path "/api/v1/campaigns" `
    -Headers @{ "Origin" = "https://evil.example"; "X-Tenant-Id" = $TenantA; "X-Workspace-Id" = "workspace-a" } `
    -Body @{ name = "synthetic-csrf-probe" } `
    -ExpectedStatus @(403)

if (-not $SkipTenantIsolation) {
    Invoke-SmokeRequest `
        -Name "Tenant scoped API rejects unauthenticated tenant A read" `
        -Method GET `
        -Path "/api/v1/subscribers" `
        -Headers @{ "X-Tenant-Id" = $TenantA; "X-Workspace-Id" = "workspace-a" } `
        -ExpectedStatus @(401, 403)

    Invoke-SmokeRequest `
        -Name "Tenant scoped API rejects unauthenticated tenant B read" `
        -Method GET `
        -Path "/api/v1/subscribers" `
        -Headers @{ "X-Tenant-Id" = $TenantB; "X-Workspace-Id" = "workspace-b" } `
        -ExpectedStatus @(401, 403)
}

Write-Host "Synthetic smoke passed for $BaseUrl"
