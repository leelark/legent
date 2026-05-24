param(
    [Parameter(Mandatory = $true)][string]$EvidenceDir,
    [string]$ManifestPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

if (-not (Test-Path $EvidenceDir)) { Fail "Missing GA evidence directory: $EvidenceDir" }
if (-not $ManifestPath) { $ManifestPath = Join-Path $EvidenceDir "ga-evidence-manifest.json" }
if (-not (Test-Path $ManifestPath)) { Fail "Missing GA evidence manifest: $ManifestPath" }
$resolvedEvidenceDir = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $EvidenceDir).Path)

function Test-ContainedPath([string]$Root, [string]$Candidate) {
    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $candidateFull = [System.IO.Path]::GetFullPath($Candidate)
    return $candidateFull.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)
}

function Resolve-EvidencePath([string]$Root, [string]$Value, [string]$Field) {
    if ([System.IO.Path]::IsPathRooted($Value)) {
        Fail "Absolute evidence paths are not allowed for ${Field}: $Value"
    }
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $Root $Value))
    if (-not (Test-ContainedPath $Root $candidate)) {
        Fail "Evidence path escapes evidence root for ${Field}: $Value"
    }
    if (-not (Test-Path $candidate)) {
        Fail "GA evidence artifact missing for ${Field}: $candidate"
    }
    return $candidate
}

function Get-JsonProperty($Object, [string]$Name) {
    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        return $null
    }
    return $Object.$Name
}

function Assert-ReviewedText($Object, [string]$Name) {
    $value = [string](Get-JsonProperty $Object $Name)
    if ([string]::IsNullOrWhiteSpace($value) -or $value -match "replace-with|placeholder|TODO|example|TBD") {
        Fail "Kafka broker topology evidence field missing or placeholder: $Name"
    }
    return $value
}

function Assert-BooleanValue($Object, [string]$Name, [bool]$Expected) {
    $value = Get-JsonProperty $Object $Name
    if ($null -eq $value) {
        Fail "Kafka broker topology evidence field missing: $Name"
    }
    if ([bool]$value -ne $Expected) {
        Fail "Kafka broker topology evidence field $Name must be $Expected"
    }
}

function Assert-MinimumInteger($Object, [string]$Name, [int]$Minimum) {
    $value = Get-JsonProperty $Object $Name
    if ($null -eq $value) {
        Fail "Kafka broker topology evidence field missing: $Name"
    }
    $number = 0
    if (-not [int]::TryParse([string]$value, [ref]$number)) {
        Fail "Kafka broker topology evidence field must be an integer: $Name"
    }
    if ($number -lt $Minimum) {
        Fail "Kafka broker topology evidence field $Name must be at least $Minimum"
    }
    return $number
}

function Test-KafkaBrokerTopologyEvidence([string]$Path) {
    $evidence = Get-Content -Path $Path -Raw | ConvertFrom-Json
    $schemaVersion = Get-JsonProperty $evidence "schemaVersion"
    if ($null -eq $schemaVersion) {
        Fail "Kafka broker topology evidence schemaVersion is missing."
    }
    if ([string]$schemaVersion -ne "1") {
        Fail "Unsupported Kafka broker topology evidence schemaVersion: $schemaVersion"
    }

    Assert-ReviewedText $evidence "reviewedBy" | Out-Null
    $reviewedAt = Assert-ReviewedText $evidence "reviewedAt"
    $reviewDate = [datetime]::MinValue
    if (-not [datetime]::TryParse($reviewedAt, [ref]$reviewDate)) {
        Fail "Kafka broker topology evidence reviewedAt must be a parseable date."
    }
    if ($reviewDate.ToUniversalTime() -gt (Get-Date).ToUniversalTime().AddMinutes(5)) {
        Fail "Kafka broker topology evidence reviewedAt cannot be in the future."
    }

    Assert-ReviewedText $evidence "clusterName" | Out-Null
    Assert-ReviewedText $evidence "provider" | Out-Null
    $brokerCount = Assert-MinimumInteger $evidence "brokerCount" 3
    $availabilityZones = @(Get-JsonProperty $evidence "availabilityZones" | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
    if ($availabilityZones.Count -lt 2) {
        Fail "Kafka broker topology evidence must include at least two availability zones."
    }

    $defaultReplicationFactor = Assert-MinimumInteger $evidence "defaultReplicationFactor" 3
    $minInSyncReplicas = Assert-MinimumInteger $evidence "minInSyncReplicas" 2
    if ($minInSyncReplicas -ge $defaultReplicationFactor) {
        Fail "Kafka broker topology minInSyncReplicas must be lower than defaultReplicationFactor."
    }
    if ($brokerCount -lt $defaultReplicationFactor) {
        Fail "Kafka broker count must be at least the default replication factor."
    }
    $producerAcks = [string](Get-JsonProperty $evidence "producerAcks")
    if ($producerAcks.ToLowerInvariant() -ne "all") {
        Fail "Kafka broker topology evidence must require producerAcks=all."
    }
    Assert-BooleanValue $evidence "uncleanLeaderElectionEnable" $false
    Assert-BooleanValue $evidence "autoCreateTopicsEnable" $false
    $underReplicatedPartitions = Assert-MinimumInteger $evidence "underReplicatedPartitions" 0
    $offlinePartitions = Assert-MinimumInteger $evidence "offlinePartitions" 0
    if ($underReplicatedPartitions -ne 0) {
        Fail "Kafka broker topology evidence must show zero under-replicated partitions."
    }
    if ($offlinePartitions -ne 0) {
        Fail "Kafka broker topology evidence must show zero offline partitions."
    }
    Assert-ReviewedText $evidence "consumerLagEvidence" | Out-Null
    Assert-ReviewedText $evidence "alertRoutingEvidence" | Out-Null

    $topics = @(Get-JsonProperty $evidence "topics")
    if ($topics.Count -eq 0) {
        Fail "Kafka broker topology evidence must include topic evidence."
    }
    $requiredTopics = [ordered]@{
        "kafka.dead-letter" = @{ partitions = 6; retentionHours = 336 }
        "email.failed.dlq" = @{ partitions = 6; retentionHours = 168 }
        "email.retry.scheduled" = @{ partitions = 6; retentionHours = 24 }
        "email.send.requested" = @{ partitions = 6; retentionHours = 24 }
        "send.audience.resolution.requested" = @{ partitions = 6; retentionHours = 24 }
        "send.audience.resolved" = @{ partitions = 6; retentionHours = 24 }
        "send.batch.created" = @{ partitions = 6; retentionHours = 24 }
        "send.processing" = @{ partitions = 6; retentionHours = 24 }
        "tracking.ingested" = @{ partitions = 6; retentionHours = 24 }
        "email.open" = @{ partitions = 6; retentionHours = 24 }
        "email.click" = @{ partitions = 6; retentionHours = 24 }
        "conversion.event" = @{ partitions = 6; retentionHours = 24 }
    }

    foreach ($topicName in $requiredTopics.Keys) {
        $topic = $topics | Where-Object { [string](Get-JsonProperty $_ "name") -eq $topicName } | Select-Object -First 1
        if ($null -eq $topic) {
            Fail "Kafka broker topology evidence missing required topic: $topicName"
        }
        $required = $requiredTopics[$topicName]
        $requiredPartitions = [int]$required["partitions"]
        $requiredRetentionHours = [int]$required["retentionHours"]
        $partitions = Assert-MinimumInteger $topic "partitions" $requiredPartitions
        if ($partitions -lt $requiredPartitions) {
            Fail "Kafka topic $topicName has insufficient partitions."
        }
        $topicReplicationFactor = Assert-MinimumInteger $topic "replicationFactor" $defaultReplicationFactor
        $topicMinInSyncReplicas = Assert-MinimumInteger $topic "minInSyncReplicas" 2
        if ($topicMinInSyncReplicas -ge $topicReplicationFactor) {
            Fail "Kafka topic $topicName minInSyncReplicas must be lower than replicationFactor."
        }
        $retentionHours = Assert-MinimumInteger $topic "retentionHours" $requiredRetentionHours
        if ($retentionHours -lt $requiredRetentionHours) {
            Fail "Kafka topic $topicName retentionHours is below required evidence threshold."
        }
        $cleanupPolicy = [string](Get-JsonProperty $topic "cleanupPolicy")
        if ($cleanupPolicy.ToLowerInvariant() -ne "delete") {
            Fail "Kafka topic $topicName cleanupPolicy must be delete."
        }
    }
}

$manifest = Get-Content -Path $ManifestPath -Raw | ConvertFrom-Json
$schemaVersionProperty = $manifest.PSObject.Properties["schemaVersion"]
if ($null -eq $schemaVersionProperty -or $null -eq $schemaVersionProperty.Value) {
    Fail "GA evidence schemaVersion is missing."
}
if ([string]$schemaVersionProperty.Value -ne "1") {
    Fail "Unsupported GA evidence schemaVersion: $($schemaVersionProperty.Value)"
}

$required = @(
    "syntheticSmoke",
    "liveLoad",
    "restoreDrill",
    "ciSecurityTranscript",
    "filesystemSbom",
    "monitoringHandoff",
    "kafkaBrokerTopology",
    "tlsCertificate",
    "restrictedAdmission",
    "registryImageEvidence"
)

foreach ($field in $required) {
    $value = [string]$manifest.$field
    if (-not $value -or $value -match "replace-with|placeholder|TODO") {
        Fail "GA evidence field missing or placeholder: $field"
    }
    $resolvedArtifactPath = Resolve-EvidencePath $resolvedEvidenceDir $value $field
    if ($field -eq "kafkaBrokerTopology") {
        Test-KafkaBrokerTopologyEvidence $resolvedArtifactPath
    }
}

Write-Host "GA evidence validation passed."
