param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("create-package", "create-sandbox", "replay-webhook", "seed-slo")]
    [string]$Command,

    [string]$ApiBase = "http://localhost:8080/api/v1",
    [string]$Token = $env:LEGENT_API_TOKEN,
    [string]$TenantId = $env:LEGENT_TENANT_ID,
    [string]$WorkspaceId = $env:LEGENT_WORKSPACE_ID,

    [string]$AppKey = "phase4-developer-platform",
    [string]$AppPackageId,
    [string]$DisplayName = "Phase 4 Developer Platform",
    [string]$ServiceName = "delivery-service",
    [string]$SourceWebhookId,
    [string]$TargetUrl,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Invoke-Legent {
    param(
        [string]$Path,
        [object]$Body
    )

    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    if ($TenantId) { $headers["X-Tenant-Id"] = $TenantId }
    if ($WorkspaceId) { $headers["X-Workspace-Id"] = $WorkspaceId }
    $headers["X-Request-Id"] = [guid]::NewGuid().ToString()

    $json = $Body | ConvertTo-Json -Depth 20
    Invoke-RestMethod -Method Post -Uri "$ApiBase$Path" -Headers $headers -ContentType "application/json" -Body $json
}

switch ($Command) {
    "create-package" {
        Invoke-Legent "/differentiation/developer/packages" @{
            appKey = $AppKey
            displayName = $DisplayName
            apiVersion = "v1"
            scopes = @("campaign:read", "workflow:*", "webhook:replay")
            sdkTargets = @("typescript", "java", "cli")
            sandboxEnabled = $true
            marketplaceStatus = "PRIVATE"
            webhookReplayEnabled = $true
            metadata = @{ source = "legent-dev.ps1" }
        }
    }
    "create-sandbox" {
        if (-not $AppPackageId) { throw "-AppPackageId is required for create-sandbox" }
        Invoke-Legent "/differentiation/developer/sandboxes" @{
            appPackageId = $AppPackageId
            dataProfile = "SMALL"
            seedOptions = @{ campaigns = 3; subscribers = 250; events = 1000 }
        }
    }
    "replay-webhook" {
        if (-not $AppPackageId) { throw "-AppPackageId is required for replay-webhook" }
        Invoke-Legent "/differentiation/developer/webhook-replays" @{
            appPackageId = $AppPackageId
            sourceWebhookId = $SourceWebhookId
            targetUrl = $TargetUrl
            dryRun = [bool]$DryRun
            eventTypes = @("campaign.sent", "tracking.click", "tracking.open")
        }
    }
    "seed-slo" {
        Invoke-Legent "/differentiation/ops/slo-policies" @{
            serviceName = $ServiceName
            sloTargetPercent = 99.9
            window = "30d"
            errorBudgetMinutes = 43.2
            syntheticProbe = @{ p95LatencyMs = 1200 }
            selfHealingActions = @(
                @{ action = "scale_workers"; threshold = "saturation>=90" },
                @{ action = "pause_low_priority_tenants"; threshold = "budget_burn>=100" }
            )
            capacityForecast = @{ unit = "workers"; horizon = "7d" }
            incidentAutomation = @{ route = "on-call"; severity = "auto" }
        }
    }
}
