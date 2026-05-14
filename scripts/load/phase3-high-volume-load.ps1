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
    [string[]]$CampaignId = @("campaign-load"),
    [string]$DataExtensionId = "load-data-extension",
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
                fileName = "load-import-$Index.csv"
                fileSize = 128
                targetType = "SUBSCRIBER"
                fieldMapping = @{
                    email = "email"
                    firstName = "firstName"
                    lastName = "lastName"
                }
            }
        }
        "segmentation-preview" {
            return @{
                fields = @("email", "firstName", "lastName")
                filters = @(@{ fieldName = "email"; operator = "CONTAINS"; value = "load" })
                limit = 100
            }
        }
        "campaign-send" {
            return @{
                triggerSource = "LOAD_TEST"
                triggerReference = "phase3-high-volume-load"
                idempotencyKey = "load-$Index"
            }
        }
        "tracking-ingest" {
            return @{
                eventName = "LOAD_CONVERSION"
                campaignId = (Get-CampaignIdForIndex -CampaignIds $campaignIdsForRequests -Index $Index)
                subscriberId = "sub-$Index"
                messageId = "msg-load-$Index"
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
    $requestPath = Resolve-ScenarioPath -ScenarioName $ScenarioName -Path $Path -Index $Index -CampaignIds $campaignIdsForRequests
    if ($DryRun) {
        return [pscustomobject]@{ success = $true; latencyMs = 0; statusCode = 0; error = $null }
    }
    try {
        if ($IsGet) {
            Invoke-RestMethod -Method Get -Uri "$BaseUrl$requestPath" -Headers (New-Headers) | Out-Null
        } else {
            $body = New-ScenarioBody -ScenarioName $ScenarioName -Index $Index
            Invoke-RestMethod -Method Post -Uri "$BaseUrl$requestPath" -Headers (New-Headers) -Body ($body | ConvertTo-Json -Depth 12) | Out-Null
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

function Split-LoadIds {
    param([object]$Value)
    $ids = @()
    foreach ($item in @($Value)) {
        if ([string]::IsNullOrWhiteSpace([string]$item)) {
            continue
        }
        $ids += @([string]$item -split "," | ForEach-Object { $_.Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    }
    return $ids
}

function Test-PlaceholderLoadId {
    param(
        [string]$Value,
        [string[]]$ReservedValues
    )
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $true
    }
    $normalized = $Value.Trim().ToLowerInvariant()
    if ($ReservedValues -contains $normalized) {
        return $true
    }
    return $normalized -match "^(placeholder|change[-_ ]?me|todo|sample|example|fake|dummy|null|none)$"
}

function Get-CampaignIdForIndex {
    param(
        [string[]]$CampaignIds,
        [int]$Index
    )
    if ($CampaignIds.Count -eq 0) {
        return ""
    }
    return $CampaignIds[($Index - 1) % $CampaignIds.Count]
}

function Resolve-ScenarioPath {
    param(
        [string]$ScenarioName,
        [string]$Path,
        [int]$Index,
        [string[]]$CampaignIds
    )
    if ($ScenarioName -eq "campaign-send") {
        $campaignIdForRequest = Get-CampaignIdForIndex -CampaignIds $CampaignIds -Index $Index
        return "/campaigns/$([uri]::EscapeDataString($campaignIdForRequest))/send"
    }
    return $Path
}

function Test-LiveScenarioInputs {
    param(
        [string[]]$CampaignIds,
        [string]$DataExtensionId,
        [int]$Segments,
        [int]$Sends,
        [int]$TrackingEvents
    )

    $errors = @()

    if ($Segments -gt 0 -and (Test-PlaceholderLoadId -Value $DataExtensionId -ReservedValues @("load-data-extension"))) {
        $errors += "-RequireLive with -Segments greater than 0 requires a real -DataExtensionId, not the default placeholder."
    }

    $needsCampaignId = $Sends -gt 0 -or $TrackingEvents -gt 0

    if ($needsCampaignId -and $CampaignIds.Count -eq 0) {
        $errors += "-RequireLive with -Sends or -TrackingEvents greater than 0 requires at least one real -CampaignId."
    }

    if ($needsCampaignId) {
        foreach ($id in $CampaignIds) {
            if (Test-PlaceholderLoadId -Value $id -ReservedValues @("campaign-load")) {
                $errors += "-RequireLive requires real -CampaignId values, not '$id'."
            }
        }
    }

    $uniqueCampaignIds = @($CampaignIds | Select-Object -Unique)
    if ($Sends -gt 1 -and $uniqueCampaignIds.Count -lt $Sends) {
        $errors += "-RequireLive send evidence requires one unique comma-separated -CampaignId per send trigger, or set -Sends 1. One trigger fans out to one seeded campaign audience; repeated sends against one campaign are not high-volume evidence."
    }

    return $errors
}

function Get-RepoRoot {
    if ($PSScriptRoot) {
        return (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    }
    return (Get-Location).Path
}

function Test-RouteMapSupport {
    param(
        [object]$Scenario,
        [object[]]$Routes
    )
    $apiPath = ([string]$Scenario.apiPath).Split("?")[0]
    $matchingRoute = $Routes |
        Where-Object { $apiPath.StartsWith([string]$_.prefix, [System.StringComparison]::OrdinalIgnoreCase) } |
        Sort-Object { ([string]$_.prefix).Length } -Descending |
        Select-Object -First 1
    if (-not $matchingRoute) {
        return "No gateway route-map prefix owns $apiPath."
    }
    if ($Scenario.service -and $matchingRoute.service -ne $Scenario.service) {
        return "Gateway route-map sends $apiPath to $($matchingRoute.service), expected $($Scenario.service)."
    }
    return $null
}

function Test-ControllerSupport {
    param(
        [string]$RepoRoot,
        [object]$Scenario
    )
    $servicePath = Join-Path $RepoRoot ("services\" + $Scenario.service + "\src\main\java")
    if (-not (Test-Path $servicePath)) {
        return "Controller source path is missing for $($Scenario.service): $servicePath"
    }

    $methodAnnotation = switch ($Scenario.method) {
        "GET" { "GetMapping" }
        "POST" { "PostMapping" }
        "PUT" { "PutMapping" }
        "DELETE" { "DeleteMapping" }
        "PATCH" { "PatchMapping" }
        default { "$($Scenario.method)Mapping" }
    }

    $rootPattern = '@RequestMapping\s*\(\s*(?:value\s*=\s*|path\s*=\s*)?(?:\{\s*)?"' +
        [regex]::Escape([string]$Scenario.controllerRoot) + '"'
    $mappingPattern = '@' + [regex]::Escape($methodAnnotation) +
        '\s*\(\s*(?:value\s*=\s*|path\s*=\s*)?(?:\{\s*)?"' +
        [regex]::Escape([string]$Scenario.controllerMapping) + '"'
    $controllerFiles = Get-ChildItem -Path $servicePath -Recurse -Filter *.java |
        Where-Object { $_.FullName -like "*\controller\*" }

    foreach ($file in $controllerFiles) {
        $content = Get-Content -Raw $file.FullName
        if (($content -match $rootPattern) -and ($content -match $mappingPattern)) {
            return $null
        }
    }

    return "No $($Scenario.service) controller declares $($Scenario.method) $($Scenario.controllerRoot)$($Scenario.controllerMapping)."
}

function Test-ScenarioContracts {
    param([object[]]$Scenarios)

    $repoRoot = Get-RepoRoot
    $routeMapPath = Join-Path $repoRoot "config\gateway\route-map.json"
    if (-not (Test-Path $routeMapPath)) {
        throw "Cannot validate load scenario routes because route-map is missing: $routeMapPath"
    }
    $routeMap = Get-Content -Raw $routeMapPath | ConvertFrom-Json

    $errors = @()
    $warnings = @()
    $liveScenarioCount = @($Scenarios | Where-Object {
            [int]$_.count -gt 0 -and $_.liveSupported -ne $false
        }).Count
    if ($RequireLive -and $liveScenarioCount -eq 0) {
        $errors += "-RequireLive requires at least one enabled live-supported scenario."
    }
    foreach ($scenario in $Scenarios) {
        if ([int]$scenario.count -lt 1) {
            continue
        }
        $routeError = Test-RouteMapSupport -Scenario $scenario -Routes @($routeMap.routes)
        if ($routeError) {
            $errors += "$($scenario.name): $routeError"
        }
        $controllerError = Test-ControllerSupport -RepoRoot $repoRoot -Scenario $scenario
        if ($controllerError) {
            $errors += "$($scenario.name): $controllerError"
        }
        if ($RequireLive -and $scenario.liveSupported -eq $false) {
            $errors += "$($scenario.name): scenario uses $($scenario.liveSupportNote) and cannot run with -RequireLive. Set its count to 0 or add a production-safe live implementation."
        } elseif ($scenario.liveSupported -eq $false) {
            $warnings += "$($scenario.name): $($scenario.liveSupportNote)."
        }
    }

    return [pscustomobject]@{
        repoRoot = $repoRoot
        routeMapPath = $routeMapPath
        errors = $errors
        warnings = $warnings
    }
}

function Invoke-RouteMapValidation {
    param([string]$RepoRoot)

    $validator = Join-Path $RepoRoot "scripts\ops\validate-route-map.ps1"
    if (-not (Test-Path $validator)) {
        throw "Cannot validate live load routes because route validator is missing: $validator"
    }

    $output = & $validator 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Route-map/Nginx/ingress validation failed before live load: $($output -join '; ')"
    }
    return ($output -join "`n")
}

$started = Get-Date
$canRunParallel = $PSVersionTable.PSVersion.Major -ge 7 -and $Concurrency -gt 1
if (-not $canRunParallel -and $Concurrency -gt 1) {
    Write-Warning "PowerShell 7 is required for parallel load. Running sequential on PowerShell $($PSVersionTable.PSVersion)."
}

$campaignIdsForRequests = @(Split-LoadIds -Value $CampaignId)
$escapedDataExtensionId = [uri]::EscapeDataString($DataExtensionId)

if ($RequireLive) {
    $liveInputErrors = @(Test-LiveScenarioInputs `
            -CampaignIds $campaignIdsForRequests `
            -DataExtensionId $DataExtensionId `
            -Segments $Segments `
            -Sends $Sends `
            -TrackingEvents $TrackingEvents)
    if ($liveInputErrors.Count -gt 0) {
        throw "Live load input validation failed: $($liveInputErrors -join '; ')"
    }
}

$scenarios = @(
    @{ name = "audience-import"; count = $Imports; path = "/imports/mock"; apiPath = "/api/v1/imports/mock"; method = "POST"; get = $false; service = "audience-service"; controllerRoot = "/api/v1/imports"; controllerMapping = "/mock"; liveSupported = $false; liveSupportNote = "the local/test import mock endpoint" },
    @{ name = "segmentation-preview"; count = $Segments; path = "/data-extensions/$escapedDataExtensionId/query-preview"; apiPath = "/api/v1/data-extensions/{deId}/query-preview"; method = "POST"; get = $false; service = "audience-service"; controllerRoot = "/api/v1/data-extensions"; controllerMapping = "/{deId}/query-preview"; liveSupported = $true },
    @{ name = "campaign-send"; count = $Sends; path = "/campaigns/{campaignId}/send"; apiPath = "/api/v1/campaigns/{id}/send"; method = "POST"; get = $false; service = "campaign-service"; controllerRoot = "/api/v1"; controllerMapping = "/campaigns/{id}/send"; liveSupported = $true },
    @{ name = "tracking-ingest"; count = $TrackingEvents; path = "/tracking/events"; apiPath = "/api/v1/tracking/events"; method = "POST"; get = $false; service = "tracking-service"; controllerRoot = "/api/v1/tracking"; controllerMapping = "/events"; liveSupported = $true },
    @{ name = "bi-report"; count = $Reports; path = "/analytics/bi/campaign-performance?limit=100"; apiPath = "/api/v1/analytics/bi/campaign-performance"; method = "GET"; get = $true; service = "tracking-service"; controllerRoot = "/api/v1/analytics/bi"; controllerMapping = "/campaign-performance"; liveSupported = $true }
)

$contractValidation = Test-ScenarioContracts -Scenarios $scenarios
if ($contractValidation.errors.Count -gt 0) {
    throw "Load scenario route validation failed: $($contractValidation.errors -join '; ')"
}
$routeMapValidationOutput = $null
if ($RequireLive) {
    $routeMapValidationOutput = Invoke-RouteMapValidation -RepoRoot $contractValidation.repoRoot
}

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
            $requestPath = $using:scenarioPath
            $ids = @($using:campaignIdsForRequests)
            $campaignIdForRequest = if ($ids.Count -gt 0) { $ids[($_ - 1) % $ids.Count] } else { "" }
            if ($using:scenarioName -eq "campaign-send") {
                $requestPath = "/campaigns/$([uri]::EscapeDataString($campaignIdForRequest))/send"
            }
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
                    Invoke-RestMethod -Method Get -Uri "$using:BaseUrl$requestPath" -Headers $headers | Out-Null
                } else {
                    $body = switch ($using:scenarioName) {
                        "audience-import" { @{ fileName = "load-import-$_.csv"; fileSize = 128; targetType = "SUBSCRIBER"; fieldMapping = @{ email = "email"; firstName = "firstName"; lastName = "lastName" } } }
                        "segmentation-preview" { @{ fields = @("email", "firstName", "lastName"); filters = @(@{ fieldName = "email"; operator = "CONTAINS"; value = "load" }); limit = 100 } }
                        "campaign-send" { @{ triggerSource = "LOAD_TEST"; triggerReference = "phase3-high-volume-load"; idempotencyKey = "load-$_" } }
                        "tracking-ingest" { @{ eventName = "LOAD_CONVERSION"; campaignId = $campaignIdForRequest; subscriberId = "sub-$_"; messageId = "msg-load-$_" } }
                        default { @{} }
                    }
                    Invoke-RestMethod -Method Post -Uri "$using:BaseUrl$requestPath" -Headers $headers -Body ($body | ConvertTo-Json -Depth 12) | Out-Null
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
    routeValidation = @{
        routeMapPath = $contractValidation.routeMapPath
        fullGatewayValidation = if ($routeMapValidationOutput) { "PASS" } else { "NOT_RUN" }
        warnings = $contractValidation.warnings
    }
    campaignSendEvidence = @{
        requestedSendTriggers = $Sends
        campaignIdCount = $campaignIdsForRequests.Count
        oneTriggerPerSeededAudience = $true
        note = "Each campaign-send request triggers one pre-seeded campaign audience. Repeated live sends against one campaign are rejected with -RequireLive."
    }
    startedAt = $started.ToUniversalTime().ToString("o")
    completedAt = (Get-Date).ToUniversalTime().ToString("o")
    scenarios = $results
    gate = if ($failedScenarios.Count -eq 0) { "PASS" } else { "FAIL" }
}

$summary | ConvertTo-Json -Depth 8

if (($RequireLive -or $FailOnErrors) -and $failedScenarios.Count -gt 0) {
    throw "Load gate failed: $($failedScenarios.scenario -join ', ') exceeded $MaxErrorRatePercent% error rate."
}
