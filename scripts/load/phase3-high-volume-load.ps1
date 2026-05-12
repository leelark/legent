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
    [string]$Token = $(if ($env:LEGENT_LOAD_TOKEN) { $env:LEGENT_LOAD_TOKEN } else { $env:LEGENT_API_TOKEN }),
    [string]$DataProfileName = "synthetic",
    [double]$MaxErrorRatePercent = 0.5,
    [switch]$RequireLive,
    [switch]$FailOnErrors,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if ($RequireLive) {
    if ($DryRun) {
        throw "-RequireLive cannot run with -DryRun."
    }
    if ([string]::IsNullOrWhiteSpace($Token)) {
        throw "-RequireLive requires -Token or LEGENT_LOAD_TOKEN/LEGENT_API_TOKEN."
    }
    if ($DataProfileName -eq "synthetic") {
        throw "-RequireLive requires -DataProfileName set to the production-like dataset identifier."
    }
}

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

function New-ScenarioBody {
    param(
        [string]$ScenarioName,
        [int]$Index
    )
    switch ($ScenarioName) {
        "audience-import" {
            return @{
                name = "load-import-$Index"
                source = "LOAD"
                rows = @(@{ email = "load$Index@example.com"; firstName = "Load"; lastName = "$Index" })
            }
        }
        "segmentation-preview" {
            return @{ sql = "select email from contacts where mod(hash(email), 10) = $($Index % 10)"; limit = 100 }
        }
        "campaign-send" {
            return @{ campaignId = "campaign-load"; subscriberId = "sub-$Index"; email = "load$Index@example.com"; messageId = "msg-load-$Index" }
        }
        "tracking-ingest" {
            return @{
                tenantId = $TenantId
                workspaceId = $WorkspaceId
                eventType = "OPEN"
                campaignId = "campaign-load"
                subscriberId = "sub-$Index"
                messageId = "msg-load-$Index"
                timestamp = (Get-Date).ToUniversalTime().ToString("o")
            }
        }
        default {
            return @{}
        }
    }
}

function Invoke-ScenarioRequest {
    param(
        [string]$ScenarioName,
        [string]$Path,
        [bool]$IsGet,
        [int]$Index
    )
    $requestStart = Get-Date
    if ($DryRun) {
        return [pscustomobject]@{ success = $true; latencyMs = 0; statusCode = 0; error = $null }
    }
    try {
        if ($IsGet) {
            Invoke-RestMethod -Method Get -Uri "$BaseUrl$Path" -Headers (New-Headers) | Out-Null
        } else {
            $body = New-ScenarioBody -ScenarioName $ScenarioName -Index $Index
            Invoke-RestMethod -Method Post -Uri "$BaseUrl$Path" -Headers (New-Headers) -Body ($body | ConvertTo-Json -Depth 12) | Out-Null
        }
        $latency = ((Get-Date) - $requestStart).TotalMilliseconds
        return [pscustomobject]@{ success = $true; latencyMs = [math]::Round($latency, 2); statusCode = 200; error = $null }
    } catch {
        $latency = ((Get-Date) - $requestStart).TotalMilliseconds
        $statusCode = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        return [pscustomobject]@{ success = $false; latencyMs = [math]::Round($latency, 2); statusCode = $statusCode; error = $_.Exception.Message }
    }
}

function Measure-P95 {
    param([object[]]$Rows)
    $latencies = @($Rows | Where-Object { $_.latencyMs -gt 0 } | ForEach-Object { [double]$_.latencyMs } | Sort-Object)
    if ($latencies.Count -eq 0) {
        return 0
    }
    $index = [Math]::Min($latencies.Count - 1, [Math]::Ceiling($latencies.Count * 0.95) - 1)
    return [math]::Round($latencies[$index], 2)
}

$started = Get-Date
$canRunParallel = $PSVersionTable.PSVersion.Major -ge 7 -and $Concurrency -gt 1
if (-not $canRunParallel -and $Concurrency -gt 1) {
    Write-Warning "PowerShell 7 is required for parallel load. Running sequential on PowerShell $($PSVersionTable.PSVersion)."
}

$scenarios = @(
    @{ name = "audience-import"; count = $Imports; path = "/audience/imports"; get = $false },
    @{ name = "segmentation-preview"; count = $Segments; path = "/audience/data-extensions/query-preview"; get = $false },
    @{ name = "campaign-send"; count = $Sends; path = "/campaigns/load-test/send"; get = $false },
    @{ name = "tracking-ingest"; count = $TrackingEvents; path = "/tracking/events"; get = $false },
    @{ name = "bi-report"; count = $Reports; path = "/analytics/bi/campaign-performance?limit=100"; get = $true }
)

$results = foreach ($scenario in $scenarios) {
    $scenarioStart = Get-Date
    $scenarioName = [string]$scenario.name
    $scenarioPath = [string]$scenario.path
    $scenarioIsGet = [bool]$scenario.get
    $scenarioCount = [int]$scenario.count

    if ($scenarioCount -lt 1) {
        [pscustomobject]@{
            scenario = $scenarioName
            requested = $scenarioCount
            completed = 0
            errors = 0
            errorRatePercent = 0
            p95LatencyMs = 0
            durationSeconds = 0
            ratePerSecond = 0
        }
        continue
    }

    if ($canRunParallel) {
        $rows = 1..$scenarioCount | ForEach-Object -Parallel {
            $requestStart = Get-Date
            if ($using:DryRun) {
                return [pscustomobject]@{ success = $true; latencyMs = 0; statusCode = 0; error = $null }
            }
            $headers = @{
                "X-Tenant-Id" = $using:TenantId
                "X-Workspace-Id" = $using:WorkspaceId
                "Content-Type" = "application/json"
            }
            if ($using:Token) {
                $headers["Authorization"] = "Bearer $using:Token"
            }
            try {
                if ($using:scenarioIsGet) {
                    Invoke-RestMethod -Method Get -Uri "$using:BaseUrl$using:scenarioPath" -Headers $headers | Out-Null
                } else {
                    $body = switch ($using:scenarioName) {
                        "audience-import" { @{ name = "load-import-$_"; source = "LOAD"; rows = @(@{ email = "load$_@example.com"; firstName = "Load"; lastName = "$_" }) } }
                        "segmentation-preview" { @{ sql = "select email from contacts where mod(hash(email), 10) = $($_ % 10)"; limit = 100 } }
                        "campaign-send" { @{ campaignId = "campaign-load"; subscriberId = "sub-$_"; email = "load$_@example.com"; messageId = "msg-load-$_" } }
                        "tracking-ingest" { @{ tenantId = $using:TenantId; workspaceId = $using:WorkspaceId; eventType = "OPEN"; campaignId = "campaign-load"; subscriberId = "sub-$_"; messageId = "msg-load-$_"; timestamp = (Get-Date).ToUniversalTime().ToString("o") } }
                        default { @{} }
                    }
                    Invoke-RestMethod -Method Post -Uri "$using:BaseUrl$using:scenarioPath" -Headers $headers -Body ($body | ConvertTo-Json -Depth 12) | Out-Null
                }
                $latency = ((Get-Date) - $requestStart).TotalMilliseconds
                [pscustomobject]@{ success = $true; latencyMs = [math]::Round($latency, 2); statusCode = 200; error = $null }
            } catch {
                $latency = ((Get-Date) - $requestStart).TotalMilliseconds
                $statusCode = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
                [pscustomobject]@{ success = $false; latencyMs = [math]::Round($latency, 2); statusCode = $statusCode; error = $_.Exception.Message }
            }
        } -ThrottleLimit $Concurrency
    } else {
        $rows = foreach ($i in 1..$scenarioCount) {
            Invoke-ScenarioRequest -ScenarioName $scenarioName -Path $scenarioPath -IsGet $scenarioIsGet -Index $i
        }
    }

    $completed = @($rows).Count
    $errors = @($rows | Where-Object { -not $_.success }).Count
    $elapsed = ((Get-Date) - $scenarioStart).TotalSeconds
    $errorRate = if ($completed -gt 0) { [math]::Round(($errors / $completed) * 100, 4) } else { 0 }
    [pscustomobject]@{
        scenario = $scenarioName
        requested = $scenarioCount
        completed = $completed
        errors = $errors
        errorRatePercent = $errorRate
        p95LatencyMs = Measure-P95 -Rows @($rows)
        durationSeconds = [math]::Round($elapsed, 2)
        ratePerSecond = if ($elapsed -gt 0) { [math]::Round($completed / $elapsed, 2) } else { $completed }
    }
}

$failedScenarios = @($results | Where-Object { $_.errorRatePercent -gt $MaxErrorRatePercent })
$summary = [pscustomobject]@{
    baseUrl = $BaseUrl
    tenantId = $TenantId
    workspaceId = $WorkspaceId
    dataProfileName = $DataProfileName
    concurrency = $Concurrency
    parallel = $canRunParallel
    requireLive = [bool]$RequireLive
    dryRun = [bool]$DryRun
    maxErrorRatePercent = $MaxErrorRatePercent
    startedAt = $started.ToUniversalTime().ToString("o")
    completedAt = (Get-Date).ToUniversalTime().ToString("o")
    scenarios = $results
    gate = if ($failedScenarios.Count -eq 0) { "PASS" } else { "FAIL" }
}

$summary | ConvertTo-Json -Depth 8

if (($RequireLive -or $FailOnErrors) -and $failedScenarios.Count -gt 0) {
    throw "Load gate failed: $($failedScenarios.scenario -join ', ') exceeded $MaxErrorRatePercent% error rate."
}
