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
    [switch]$DryRun,
    [string]$LiveEvidencePath,
    [string]$EvidenceOutputPath
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

function Test-EvidencePlaceholderValue {
    param([object]$Value)

    if ($null -eq $Value) {
        return $true
    }
    $text = ([string]$Value).Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $true
    }
    $normalized = $text.ToLowerInvariant()
    if ($normalized -match "^(placeholder|change[-_ ]?me|todo|sample|example|fake|dummy|null|none|n/?a|synthetic|local|localhost|dev)$") {
        return $true
    }
    if ($normalized -match "(localhost|127\.0\.0\.1|0\.0\.0\.0|example\.com|\.local\b)") {
        return $true
    }
    return $false
}

function Get-EvidenceValue {
    param(
        [object]$Evidence,
        [string]$Path
    )

    $current = $Evidence
    foreach ($segment in ($Path -split "\.")) {
        if ($null -eq $current) {
            return $null
        }
        $property = $current.PSObject.Properties[$segment]
        if ($null -eq $property) {
            return $null
        }
        $current = $property.Value
    }
    return $current
}

function Convert-EvidenceNumber {
    param([object]$Value)

    if ($null -eq $Value) {
        return $null
    }
    try {
        $number = [double]$Value
        if ([double]::IsNaN($number) -or [double]::IsInfinity($number)) {
            return $null
        }
        return $number
    } catch {
        return $null
    }
}

function Find-EvidenceSecretFields {
    param(
        [object]$Value,
        [string]$Path = "$"
    )

    $findings = @()
    if ($null -eq $Value) {
        return $findings
    }

    if ($Value -is [string]) {
        if ($Value -match "(?i)(bearer\s+[a-z0-9._~+/=-]{12,}|authorization\s*:|password\s*=|token\s*=|api[-_]?key\s*=|-----BEGIN [A-Z ]*PRIVATE KEY|AKIA[0-9A-Z]{16}|x-amz-security-token)") {
            $findings += "$Path contains secret-like text."
        }
        return $findings
    }

    if ($Value -is [System.ValueType]) {
        return $findings
    }

    if ($Value -is [System.Collections.IEnumerable]) {
        $index = 0
        foreach ($item in $Value) {
            $findings += Find-EvidenceSecretFields -Value $item -Path "$Path[$index]"
            $index++
        }
        return $findings
    }

    foreach ($property in $Value.PSObject.Properties) {
        $propertyPath = "$Path.$($property.Name)"
        if ($property.Name -match "(?i)(password|passwd|secret|token|authorization|cookie|private[-_]?key|api[-_]?key|client[-_]?secret|credential|access[-_]?key)") {
            $findings += "$propertyPath has a secret-like field name."
        }
        $findings += Find-EvidenceSecretFields -Value $property.Value -Path $propertyPath
    }
    return $findings
}

function Resolve-EvidencePath {
    param(
        [string]$Path,
        [string]$RepoRoot
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $Path))
}

function Test-LiveEvidenceFile {
    param(
        [string]$Path,
        [string]$RepoRoot,
        [bool]$Required
    )

    $errors = @()
    $warnings = @()
    $resolvedPath = Resolve-EvidencePath -Path $Path -RepoRoot $RepoRoot

    if ([string]::IsNullOrWhiteSpace($resolvedPath)) {
        if ($Required) {
            $errors += "-RequireLive requires -LiveEvidencePath with target evidence for ClickHouse TTL/partitioning, Postgres purge, remote render latency, Kafka handoff pressure, and delivery provider capacity."
        }
        return [pscustomobject]@{
            required = $Required
            provided = $false
            sourcePath = $null
            gate = if ($errors.Count -eq 0) { "NOT_RUN" } else { "FAIL" }
            errors = $errors
            warnings = $warnings
            evidence = $null
        }
    }

    if (-not (Test-Path $resolvedPath)) {
        $errors += "Live evidence file does not exist: $resolvedPath"
        return [pscustomobject]@{
            required = $Required
            provided = $true
            sourcePath = $resolvedPath
            gate = "FAIL"
            errors = $errors
            warnings = $warnings
            evidence = $null
        }
    }

    try {
        $evidence = Get-Content -Raw -Path $resolvedPath | ConvertFrom-Json
    } catch {
        $errors += "Live evidence file is not valid JSON: $($_.Exception.Message)"
        return [pscustomobject]@{
            required = $Required
            provided = $true
            sourcePath = $resolvedPath
            gate = "FAIL"
            errors = $errors
            warnings = $warnings
            evidence = $null
        }
    }

    $secretFindings = @(Find-EvidenceSecretFields -Value $evidence)
    if ($secretFindings.Count -gt 0) {
        $errors += "Live evidence JSON must not contain secrets or secret-like fields: $($secretFindings -join '; ')"
    }

    $textRequirements = @(
        @{ path = "environment"; description = "target environment name" },
        @{ path = "observedAt"; description = "target observation timestamp" },
        @{ path = "clickHouse.rawEventsTable"; description = "ClickHouse raw event table" },
        @{ path = "clickHouse.partitionKey"; description = "ClickHouse raw event partition key" },
        @{ path = "clickHouse.ttlExpression"; description = "ClickHouse raw event TTL expression" },
        @{ path = "clickHouse.proofRef"; description = "ClickHouse target proof reference" },
        @{ path = "postgres.retentionFunction"; description = "Postgres retention function" },
        @{ path = "postgres.purgeProofRef"; description = "Postgres purge target proof reference" },
        @{ path = "remoteRender.proofRef"; description = "remote render latency target proof reference" },
        @{ path = "kafkaHandoff.topic"; description = "Kafka handoff topic" },
        @{ path = "kafkaHandoff.consumerGroup"; description = "Kafka handoff consumer group" },
        @{ path = "kafkaHandoff.proofRef"; description = "Kafka handoff pressure target proof reference" },
        @{ path = "deliveryProviderCapacity.profileName"; description = "delivery provider capacity profile" },
        @{ path = "deliveryProviderCapacity.throttleState"; description = "delivery provider throttle state" },
        @{ path = "deliveryProviderCapacity.proofRef"; description = "delivery provider capacity target proof reference" }
    )
    foreach ($requirement in $textRequirements) {
        $value = Get-EvidenceValue -Evidence $evidence -Path $requirement.path
        if (Test-EvidencePlaceholderValue -Value $value) {
            $errors += "-LiveEvidencePath must include $($requirement.path) ($($requirement.description)) with a non-placeholder target value."
        }
    }

    $numberRequirements = @(
        @{ path = "clickHouse.ttlDays"; min = 1; description = "ClickHouse raw event TTL days" },
        @{ path = "clickHouse.partitionCount"; min = 1; description = "ClickHouse observed partition count" },
        @{ path = "postgres.retentionDays"; min = 1; description = "Postgres raw event retention days" },
        @{ path = "postgres.lastPurgeRows"; min = 0; description = "last Postgres purge row count" },
        @{ path = "postgres.lastPurgeDurationMs"; min = 0; description = "last Postgres purge duration" },
        @{ path = "remoteRender.sampleCount"; min = 1; description = "remote render latency sample count" },
        @{ path = "remoteRender.p95Ms"; min = 0; description = "remote render p95 latency" },
        @{ path = "remoteRender.maxP95Ms"; min = 1; description = "remote render p95 gate" },
        @{ path = "kafkaHandoff.maxConsumerLag"; min = 0; description = "Kafka max consumer lag" },
        @{ path = "kafkaHandoff.maxAllowedConsumerLag"; min = 0; description = "Kafka max allowed consumer lag" },
        @{ path = "kafkaHandoff.p95PublishLatencyMs"; min = 0; description = "Kafka p95 publish latency" },
        @{ path = "kafkaHandoff.maxPublishLatencyMs"; min = 1; description = "Kafka publish latency gate" },
        @{ path = "deliveryProviderCapacity.warmedDomains"; min = 1; description = "warmed provider domains" },
        @{ path = "deliveryProviderCapacity.approvedPerMinute"; min = 1; description = "approved provider capacity per minute" },
        @{ path = "deliveryProviderCapacity.plannedPeakPerMinute"; min = 1; description = "planned peak send rate per minute" }
    )
    foreach ($requirement in $numberRequirements) {
        $number = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path $requirement.path)
        if ($null -eq $number) {
            $errors += "-LiveEvidencePath must include numeric $($requirement.path) ($($requirement.description))."
        } elseif ($number -lt [double]$requirement.min) {
            $errors += "-LiveEvidencePath $($requirement.path) must be at least $($requirement.min); got $number."
        }
    }

    $observedAt = [string](Get-EvidenceValue -Evidence $evidence -Path "observedAt")
    if (-not [string]::IsNullOrWhiteSpace($observedAt)) {
        try {
            [datetimeoffset]::Parse($observedAt) | Out-Null
        } catch {
            $errors += "-LiveEvidencePath observedAt must be an ISO-8601-compatible timestamp."
        }
    }

    $partitionKey = [string](Get-EvidenceValue -Evidence $evidence -Path "clickHouse.partitionKey")
    if (-not [string]::IsNullOrWhiteSpace($partitionKey) -and ($partitionKey -notmatch "toYYYYMM" -or $partitionKey -notmatch "timestamp")) {
        $errors += "-LiveEvidencePath clickHouse.partitionKey must prove partitioning by event timestamp month, for example toYYYYMM(timestamp)."
    }
    $ttlExpression = [string](Get-EvidenceValue -Evidence $evidence -Path "clickHouse.ttlExpression")
    if (-not [string]::IsNullOrWhiteSpace($ttlExpression) -and ($ttlExpression -notmatch "timestamp" -or $ttlExpression -notmatch "(?i)delete")) {
        $errors += "-LiveEvidencePath clickHouse.ttlExpression must prove timestamp-based DELETE TTL."
    }
    $retentionFunction = [string](Get-EvidenceValue -Evidence $evidence -Path "postgres.retentionFunction")
    if (-not [string]::IsNullOrWhiteSpace($retentionFunction) -and $retentionFunction -ne "purge_expired_raw_events") {
        $errors += "-LiveEvidencePath postgres.retentionFunction must be purge_expired_raw_events."
    }

    $remoteP95 = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "remoteRender.p95Ms")
    $remoteMax = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "remoteRender.maxP95Ms")
    if ($null -ne $remoteP95 -and $null -ne $remoteMax -and $remoteP95 -gt $remoteMax) {
        $errors += "Remote render p95 latency gate failed: $remoteP95 ms exceeds $remoteMax ms."
    }

    $consumerLag = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.maxConsumerLag")
    $maxConsumerLag = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.maxAllowedConsumerLag")
    if ($null -ne $consumerLag -and $null -ne $maxConsumerLag -and $consumerLag -gt $maxConsumerLag) {
        $errors += "Kafka handoff pressure gate failed: consumer lag $consumerLag exceeds allowed $maxConsumerLag."
    }
    $publishP95 = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.p95PublishLatencyMs")
    $publishMax = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.maxPublishLatencyMs")
    if ($null -ne $publishP95 -and $null -ne $publishMax -and $publishP95 -gt $publishMax) {
        $errors += "Kafka handoff publish latency gate failed: $publishP95 ms exceeds $publishMax ms."
    }

    $approvedCapacity = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "deliveryProviderCapacity.approvedPerMinute")
    $plannedCapacity = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "deliveryProviderCapacity.plannedPeakPerMinute")
    if ($null -ne $approvedCapacity -and $null -ne $plannedCapacity -and $approvedCapacity -lt $plannedCapacity) {
        $errors += "Delivery provider capacity gate failed: approved $approvedCapacity/min is below planned peak $plannedCapacity/min."
    }
    $throttleState = [string](Get-EvidenceValue -Evidence $evidence -Path "deliveryProviderCapacity.throttleState")
    if (-not [string]::IsNullOrWhiteSpace($throttleState)) {
        $normalizedThrottleState = $throttleState.Trim().ToUpperInvariant()
        if (@("BLOCKED", "UNKNOWN", "UNAVAILABLE", "FAILED") -contains $normalizedThrottleState) {
            $errors += "Delivery provider capacity gate failed: throttleState is $normalizedThrottleState."
        } elseif (@("THROTTLED", "CAUTIOUS") -contains $normalizedThrottleState) {
            $warnings += "Delivery provider capacity is $normalizedThrottleState; keep planned launch rate at or below the approved capacity profile."
        }
    }

    $structuredEvidence = [ordered]@{
        sourcePath = $resolvedPath
        environment = [string](Get-EvidenceValue -Evidence $evidence -Path "environment")
        observedAt = [string](Get-EvidenceValue -Evidence $evidence -Path "observedAt")
        clickHouse = [ordered]@{
            rawEventsTable = [string](Get-EvidenceValue -Evidence $evidence -Path "clickHouse.rawEventsTable")
            partitionKey = [string](Get-EvidenceValue -Evidence $evidence -Path "clickHouse.partitionKey")
            ttlExpression = [string](Get-EvidenceValue -Evidence $evidence -Path "clickHouse.ttlExpression")
            ttlDays = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "clickHouse.ttlDays")
            partitionCount = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "clickHouse.partitionCount")
            proofRef = [string](Get-EvidenceValue -Evidence $evidence -Path "clickHouse.proofRef")
        }
        postgres = [ordered]@{
            retentionFunction = [string](Get-EvidenceValue -Evidence $evidence -Path "postgres.retentionFunction")
            retentionDays = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "postgres.retentionDays")
            lastPurgeRows = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "postgres.lastPurgeRows")
            lastPurgeDurationMs = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "postgres.lastPurgeDurationMs")
            purgeProofRef = [string](Get-EvidenceValue -Evidence $evidence -Path "postgres.purgeProofRef")
        }
        remoteRender = [ordered]@{
            sampleCount = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "remoteRender.sampleCount")
            p95Ms = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "remoteRender.p95Ms")
            maxP95Ms = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "remoteRender.maxP95Ms")
            proofRef = [string](Get-EvidenceValue -Evidence $evidence -Path "remoteRender.proofRef")
        }
        kafkaHandoff = [ordered]@{
            topic = [string](Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.topic")
            consumerGroup = [string](Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.consumerGroup")
            maxConsumerLag = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.maxConsumerLag")
            maxAllowedConsumerLag = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.maxAllowedConsumerLag")
            p95PublishLatencyMs = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.p95PublishLatencyMs")
            maxPublishLatencyMs = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.maxPublishLatencyMs")
            proofRef = [string](Get-EvidenceValue -Evidence $evidence -Path "kafkaHandoff.proofRef")
        }
        deliveryProviderCapacity = [ordered]@{
            profileName = [string](Get-EvidenceValue -Evidence $evidence -Path "deliveryProviderCapacity.profileName")
            warmedDomains = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "deliveryProviderCapacity.warmedDomains")
            approvedPerMinute = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "deliveryProviderCapacity.approvedPerMinute")
            plannedPeakPerMinute = Convert-EvidenceNumber -Value (Get-EvidenceValue -Evidence $evidence -Path "deliveryProviderCapacity.plannedPeakPerMinute")
            throttleState = [string](Get-EvidenceValue -Evidence $evidence -Path "deliveryProviderCapacity.throttleState")
            proofRef = [string](Get-EvidenceValue -Evidence $evidence -Path "deliveryProviderCapacity.proofRef")
        }
    }

    return [pscustomobject]@{
        required = $Required
        provided = $true
        sourcePath = $resolvedPath
        gate = if ($errors.Count -eq 0) { "PASS" } else { "FAIL" }
        errors = $errors
        warnings = $warnings
        evidence = $structuredEvidence
    }
}

function Write-EvidenceJson {
    param(
        [string]$Path,
        [string]$Json
    )

    $directory = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($directory) -and -not (Test-Path $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Json, [System.Text.Encoding]::UTF8)
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
$repoRoot = Get-RepoRoot
$liveEvidenceValidation = Test-LiveEvidenceFile `
    -Path $LiveEvidencePath `
    -RepoRoot $repoRoot `
    -Required ([bool]$RequireLive)

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
if (($RequireLive -or -not [string]::IsNullOrWhiteSpace($LiveEvidencePath)) -and $liveEvidenceValidation.errors.Count -gt 0) {
    throw "Live evidence validation failed: $($liveEvidenceValidation.errors -join '; ')"
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
$resolvedEvidenceOutputPath = Resolve-EvidencePath -Path $EvidenceOutputPath -RepoRoot $contractValidation.repoRoot
$evidenceOutputRequested = -not [string]::IsNullOrWhiteSpace($resolvedEvidenceOutputPath)
$summary = [pscustomobject]@{
    schemaVersion = "phase3-high-volume-load/v2"
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
    liveEvidence = @{
        required = $liveEvidenceValidation.required
        provided = $liveEvidenceValidation.provided
        sourcePath = $liveEvidenceValidation.sourcePath
        gate = $liveEvidenceValidation.gate
        warnings = $liveEvidenceValidation.warnings
        errors = $liveEvidenceValidation.errors
        proof = $liveEvidenceValidation.evidence
    }
    evidenceOutput = @{
        jsonPath = $resolvedEvidenceOutputPath
        written = $evidenceOutputRequested
        secretPolicy = "Authorization headers, tokens, cookies, private keys, credentials, and secret-like evidence fields are not emitted; -LiveEvidencePath is rejected if they are detected."
    }
    startedAt = $started.ToUniversalTime().ToString("o")
    completedAt = (Get-Date).ToUniversalTime().ToString("o")
    scenarios = $results
    gate = if ($failedScenarios.Count -eq 0) { "PASS" } else { "FAIL" }
}

$summaryJson = $summary | ConvertTo-Json -Depth 12
if ($evidenceOutputRequested) {
    Write-EvidenceJson -Path $resolvedEvidenceOutputPath -Json $summaryJson
}
$summaryJson

if (($RequireLive -or $FailOnErrors) -and $failedScenarios.Count -gt 0) {
    throw "Load gate failed: $($failedScenarios.scenario -join ', ') exceeded $MaxErrorRatePercent% error rate."
}
