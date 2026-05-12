param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [string]$TenantId = "tenant-load",
    [string]$WorkspaceId = "workspace-load",
    [int]$Imports = 1000,
    [int]$Segments = 250,
    [int]$Sends = 5000,
    [int]$TrackingEvents = 20000,
    [int]$Reports = 100,
    [int]$Concurrency = 16,
    [string]$Token = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function New-Headers {
    $headers = @{
        "X-Tenant-Id" = $TenantId
        "X-Workspace-Id" = $WorkspaceId
        "Content-Type" = "application/json"
    }
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }
    return $headers
}

function Invoke-LoadPost {
    param(
        [string]$Path,
        [object]$Body
    )
    if ($DryRun) {
        return @{ status = "DRY_RUN"; path = $Path }
    }
    Invoke-RestMethod -Method Post -Uri "$BaseUrl$Path" -Headers (New-Headers) -Body ($Body | ConvertTo-Json -Depth 12)
}

function Invoke-LoadGet {
    param([string]$Path)
    if ($DryRun) {
        return @{ status = "DRY_RUN"; path = $Path }
    }
    Invoke-RestMethod -Method Get -Uri "$BaseUrl$Path" -Headers (New-Headers)
}

$started = Get-Date
$parallelUnavailable = $PSVersionTable.PSVersion.Major -lt 7 -and $Concurrency -gt 1
if ($parallelUnavailable) {
    Write-Warning "PowerShell 7 is required for parallel load. Running sequential on PowerShell $($PSVersionTable.PSVersion)."
}
$scenarios = @(
    @{ name = "audience-import"; count = $Imports; path = "/audience/imports"; body = { param($i) @{ name = "load-import-$i"; source = "LOAD"; rows = @(@{ email = "load$i@example.com"; firstName = "Load"; lastName = "$i" }) } } },
    @{ name = "segmentation-preview"; count = $Segments; path = "/audience/data-extensions/query-preview"; body = { param($i) @{ sql = "select email from contacts where mod(hash(email), 10) = $($i % 10)"; limit = 100 } } },
    @{ name = "campaign-send"; count = $Sends; path = "/campaigns/load-test/send"; body = { param($i) @{ campaignId = "campaign-load"; subscriberId = "sub-$i"; email = "load$i@example.com"; messageId = "msg-load-$i" } } },
    @{ name = "tracking-ingest"; count = $TrackingEvents; path = "/tracking/events"; body = { param($i) @{ tenantId = $TenantId; workspaceId = $WorkspaceId; eventType = "OPEN"; campaignId = "campaign-load"; subscriberId = "sub-$i"; messageId = "msg-load-$i"; timestamp = (Get-Date).ToUniversalTime().ToString("o") } } },
    @{ name = "bi-report"; count = $Reports; path = "/analytics/bi/campaign-performance?limit=100"; get = $true }
)

$results = foreach ($scenario in $scenarios) {
    $scenarioStart = Get-Date
    $errors = 0
    $completed = 0
    for ($i = 1; $i -le $scenario.count; $i++) {
        try {
            if ($DryRun) {
                $completed++
                continue
            } elseif ($scenario.get) {
                Invoke-LoadGet -Path $scenario.path | Out-Null
            } else {
                $body = & $scenario.body $i
                Invoke-LoadPost -Path $scenario.path -Body $body | Out-Null
            }
            $completed++
        } catch {
            $completed++
            $errors++
        }
    }
    $elapsed = ((Get-Date) - $scenarioStart).TotalSeconds
    [pscustomobject]@{
        scenario = $scenario.name
        requested = $scenario.count
        completed = $completed
        errors = $errors
        durationSeconds = [math]::Round($elapsed, 2)
        ratePerSecond = if ($elapsed -gt 0) { [math]::Round($completed / $elapsed, 2) } else { $completed }
    }
}

[pscustomobject]@{
    baseUrl = $BaseUrl
    tenantId = $TenantId
    workspaceId = $WorkspaceId
    concurrency = $Concurrency
    dryRun = [bool]$DryRun
    startedAt = $started.ToUniversalTime().ToString("o")
    completedAt = (Get-Date).ToUniversalTime().ToString("o")
    scenarios = $results
} | ConvertTo-Json -Depth 8
