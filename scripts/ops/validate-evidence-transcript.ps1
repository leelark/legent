param(
    [Parameter(Mandatory = $true)]
    [string] $TranscriptPath,

    [Parameter(Mandatory = $true)]
    [ValidateSet("live-load", "restore-drill", "security-scan", "monitoring-handoff")]
    [string] $Type,

    [string] $EvidenceRoot,

    [int] $MaxAgeDays = 14
)

$ErrorActionPreference = "Stop"

if ($MaxAgeDays -lt 1) {
    throw "MaxAgeDays must be at least 1"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Get-FullPath([string] $Path, [string] $BasePath) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "Path value is required"
    }

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $BasePath $Path))
}

function Test-IsPathUnderRoot([string] $Path, [string] $Root) {
    $fullPath = [System.IO.Path]::GetFullPath($Path).TrimEnd("\", "/")
    $fullRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd("\", "/")
    if ([string]::Equals($fullPath, $fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }

    $rootPrefix = "$fullRoot$([System.IO.Path]::DirectorySeparatorChar)"
    return $fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-SecretLikePath([string] $Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }

    $normalized = $Path -replace "/", "\"
    $leaf = ([System.IO.Path]::GetFileName($normalized)).ToLowerInvariant()
    $segments = @($normalized -split "\\+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

    if ($leaf -match "^\.env($|\.)" -or $leaf -match "^env\.[^.]+$" -or $leaf -match "\.env($|\.)") {
        return $true
    }
    if ($leaf -match "^(secrets?|credentials?)(\.[^.]+)?$") {
        return $true
    }
    if ($leaf -match "\.(pem|key|p12|pfx|jks|keystore)$") {
        return $true
    }
    if ($leaf -match "^(id_rsa|id_dsa|id_ecdsa|id_ed25519|kubeconfig)(\..*)?$") {
        return $true
    }

    foreach ($segment in $segments) {
        $lowerSegment = $segment.ToLowerInvariant()
        if ($lowerSegment -in @(".env", ".ssh", "secrets", "credentials")) {
            return $true
        }
    }

    return $false
}

function Get-PlaceholderMatch([string] $Text) {
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }

    $placeholderPatterns = @(
        "(?i)\bTBD\b",
        "(?i)\bTODO\b",
        "(?i)\bFIXME\b",
        "(?i)\bCHANGEME\b",
        "(?i)\bCHANGE_ME\b",
        "(?i)\bREPLACE_ME\b",
        "(?i)\breplace_with_[a-z0-9_]+\b",
        "(?i)\byour_[a-z0-9_]+\b",
        "(?i)<\s*[^>\r\n]+\s*>",
        "(?i)\bexample\.(com|org|net)\b",
        "(?i)\bplaceholder\b"
    )

    foreach ($pattern in $placeholderPatterns) {
        $match = [regex]::Match($Text, $pattern)
        if ($match.Success) {
            return $match.Value
        }
    }

    return $null
}

function Get-ObjectPropertyValue($Object, [string[]] $Names) {
    if ($null -eq $Object) {
        return $null
    }

    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property -and $null -ne $property.Value) {
            return $property.Value
        }
    }

    return $null
}

function Test-PassingStatus($Value) {
    if ($null -eq $Value) {
        return $false
    }

    return ([string] $Value) -match "^(?i:pass|passed|success|succeeded|complete|completed|verified|approved|ok)$"
}

function Test-TrueValue($Value) {
    if ($Value -is [bool]) {
        return [bool] $Value
    }

    return ([string] $Value) -match "^(?i:true|yes|y|1|pass|passed|verified)$"
}

function Test-FalseValue($Value) {
    if ($Value -is [bool]) {
        return -not [bool] $Value
    }

    return ([string] $Value) -match "^(?i:false|no|n|0|none|redacted)$"
}

function Parse-EvidenceDate([string] $DateValue, [string] $FieldName) {
    if ([string]::IsNullOrWhiteSpace($DateValue)) {
        throw "$FieldName is required"
    }

    $parsed = [datetimeoffset]::MinValue
    $style = [System.Globalization.DateTimeStyles]::AssumeLocal
    if (-not [datetimeoffset]::TryParse($DateValue, [System.Globalization.CultureInfo]::InvariantCulture, $style, [ref] $parsed)) {
        throw "$FieldName is not an ISO-8601-compatible timestamp: $DateValue"
    }

    return $parsed
}

function Test-UriReference([string] $Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    return $Value -match "^[a-z][a-z0-9+.-]*://"
}

function Find-SecretLikeText {
    param(
        [object] $Value,
        [string] $Path = "$"
    )

    $findings = New-Object System.Collections.Generic.List[string]

    if ($null -eq $Value) {
        return @()
    }

    if ($Value -is [string]) {
        $text = [string] $Value
        $secretPatterns = @(
            "(?i)\bBearer\s+[A-Za-z0-9._~+/=-]{12,}",
            "(?i)\bAuthorization\s*[:=]\s*\S+",
            "(?i)\bCookie\s*[:=]\s*\S+",
            "(?i)(password|passwd|secret|api[-_]?key|client[-_]?secret|access[-_]?key)\s*[:=]\s*[^\s*][^\r\n]{3,}",
            "-----BEGIN [A-Z ]*PRIVATE KEY-----",
            "AKIA[0-9A-Z]{16}",
            "(?i)x-amz-security-token"
        )

        foreach ($pattern in $secretPatterns) {
            if ($text -match $pattern) {
                $findings.Add("$Path contains secret-like text")
                break
            }
        }

        if ($text -match "(?i)(^|\s)-Token\s+[""']?(?!`$env:|REDACTED|redacted|\*)[A-Za-z0-9._~+/=-]{12,}") {
            $findings.Add("$Path appears to pass a raw token value")
        }

        return @($findings)
    }

    if ($Value -is [System.ValueType]) {
        return @()
    }

    if ($Value -is [System.Collections.IEnumerable]) {
        $index = 0
        foreach ($item in $Value) {
            foreach ($finding in (Find-SecretLikeText -Value $item -Path "$Path[$index]")) {
                $findings.Add($finding)
            }
            $index++
        }
        return @($findings)
    }

    foreach ($property in $Value.PSObject.Properties) {
        $propertyPath = "$Path.$($property.Name)"
        $allowedSecretReviewField = $property.Name -match "^(?i:containsSecrets|secretsPresent|hasSecrets)$"
        if ((-not $allowedSecretReviewField) -and $property.Name -match "(?i)(password|passwd|secret|authorization|cookie|private[-_]?key|api[-_]?key|client[-_]?secret|credential|access[-_]?key)") {
            $findings.Add("$propertyPath has a secret-like field name")
        }

        foreach ($finding in (Find-SecretLikeText -Value $property.Value -Path $propertyPath)) {
            $findings.Add($finding)
        }
    }

    return @($findings)
}

function Assert-RecentDate($DateValue, [string] $FieldName, [datetime] $Cutoff, [datetime] $Now) {
    $parsed = Parse-EvidenceDate ([string] $DateValue) $FieldName
    $localDate = $parsed.LocalDateTime
    if ($localDate -lt $Cutoff) {
        throw "$FieldName is stale: $parsed is older than $MaxAgeDays days"
    }
    if ($localDate -gt $Now.AddDays(1)) {
        throw "$FieldName is dated in the future: $parsed"
    }
}

function Assert-RequiredText($Value, [string] $FieldName) {
    $text = [string] $Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        throw "$FieldName is required"
    }

    $placeholder = Get-PlaceholderMatch $text
    if ($null -ne $placeholder) {
        throw "$FieldName contains placeholder text '$placeholder'"
    }
}

function Assert-LocalEvidenceFile([string] $Reference, [string] $EvidenceRootPath) {
    if ([string]::IsNullOrWhiteSpace($Reference) -or (Test-UriReference $Reference)) {
        return ""
    }

    if (Test-SecretLikePath $Reference) {
        throw "Artifact reference points to a secret/env-like path: $Reference"
    }

    $fullPath = Get-FullPath $Reference $EvidenceRootPath
    if (-not (Test-IsPathUnderRoot $fullPath $EvidenceRootPath)) {
        throw "Artifact reference escapes the evidence root: $Reference"
    }
    if (Test-SecretLikePath $fullPath) {
        throw "Artifact reference resolves to a secret/env-like path: $fullPath"
    }
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "Artifact reference is missing: $Reference"
    }

    $fileText = Get-Content -Raw -Encoding UTF8 -LiteralPath $fullPath
    $placeholder = Get-PlaceholderMatch $fileText
    if ($null -ne $placeholder) {
        throw "Artifact file '$Reference' contains placeholder text '$placeholder'"
    }

    $secretFindings = @(Find-SecretLikeText -Value $fileText -Path $Reference)
    if ($secretFindings.Count -gt 0) {
        throw "Artifact file '$Reference' contains secret-like text: $($secretFindings -join '; ')"
    }

    return $fileText
}

$transcriptFullPath = Get-FullPath $TranscriptPath $repoRoot
if (-not (Test-Path -LiteralPath $transcriptFullPath -PathType Leaf)) {
    throw "Evidence transcript not found: $TranscriptPath"
}
if (Test-SecretLikePath $transcriptFullPath) {
    throw "Evidence transcript path looks like a secret/env path: $transcriptFullPath"
}
if ([System.IO.Path]::GetExtension($transcriptFullPath).ToLowerInvariant() -ne ".json") {
    throw "Evidence transcript must be JSON: $transcriptFullPath"
}

if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $evidenceRootFullPath = Split-Path -Parent $transcriptFullPath
} else {
    $evidenceRootFullPath = Get-FullPath $EvidenceRoot $repoRoot
}
if (-not (Test-Path -LiteralPath $evidenceRootFullPath -PathType Container)) {
    throw "Evidence root not found: $EvidenceRoot"
}
if (Test-SecretLikePath $evidenceRootFullPath) {
    throw "Evidence root looks like a secret/env path: $evidenceRootFullPath"
}

$raw = Get-Content -Raw -Encoding UTF8 -LiteralPath $transcriptFullPath
$placeholder = Get-PlaceholderMatch $raw
if ($null -ne $placeholder) {
    throw "Evidence transcript contains placeholder text '$placeholder'"
}

try {
    $transcript = $raw | ConvertFrom-Json
} catch {
    throw "Evidence transcript is not valid JSON: $($_.Exception.Message)"
}

$secretFindings = @(Find-SecretLikeText -Value $transcript)
if ($secretFindings.Count -gt 0) {
    throw "Evidence transcript must not contain secrets or secret-like fields: $($secretFindings -join '; ')"
}

Assert-RequiredText (Get-ObjectPropertyValue $transcript @("schemaVersion")) "schemaVersion"
if ([string](Get-ObjectPropertyValue $transcript @("schemaVersion")) -ne "legent.evidence-transcript.v1") {
    throw "schemaVersion must be legent.evidence-transcript.v1"
}

if ([string](Get-ObjectPropertyValue $transcript @("evidenceType", "type")) -ne $Type) {
    throw "evidenceType must be '$Type'"
}

if (-not (Test-PassingStatus (Get-ObjectPropertyValue $transcript @("status", "result", "outcome")))) {
    throw "Evidence transcript status/result/outcome must be pass"
}

Assert-RequiredText (Get-ObjectPropertyValue $transcript @("environment")) "environment"
Assert-RequiredText (Get-ObjectPropertyValue $transcript @("releaseVersion")) "releaseVersion"
Assert-RequiredText (Get-ObjectPropertyValue $transcript @("owner", "operator")) "owner"

$now = Get-Date
$cutoff = $now.AddDays(-$MaxAgeDays)
Assert-RecentDate (Get-ObjectPropertyValue $transcript @("capturedAt", "completedAt", "generatedAt", "runAt")) "capturedAt/completedAt" $cutoff $now

$sanitization = Get-ObjectPropertyValue $transcript @("sanitization", "sanitizationReview")
if ($null -eq $sanitization) {
    throw "sanitization review is required"
}
if (-not (Test-TrueValue (Get-ObjectPropertyValue $sanitization @("reviewed", "approved", "sanitized")))) {
    throw "sanitization.reviewed must be true"
}
if (-not (Test-FalseValue (Get-ObjectPropertyValue $sanitization @("containsSecrets", "secretsPresent", "hasSecrets")))) {
    throw "sanitization.containsSecrets must be false"
}
if (-not (Test-TrueValue (Get-ObjectPropertyValue $sanitization @("commandOutputsSanitized", "outputsSanitized")))) {
    throw "sanitization.commandOutputsSanitized must be true"
}

$commands = @(Get-ObjectPropertyValue $transcript @("commands", "commandRefs"))
if ($commands.Count -eq 0 -or $null -eq $commands[0]) {
    throw "commands must include at least one command reference"
}

foreach ($command in $commands) {
    Assert-RequiredText (Get-ObjectPropertyValue $command @("command", "commandLine")) "commands.command"
    if (-not (Test-PassingStatus (Get-ObjectPropertyValue $command @("status", "result", "outcome")))) {
        throw "Every command reference must have a passing status/result/outcome"
    }

    $exitCode = Get-ObjectPropertyValue $command @("exitCode")
    if ($null -eq $exitCode -or [int] $exitCode -ne 0) {
        throw "Every command reference must include exitCode 0"
    }

    Assert-RecentDate (Get-ObjectPropertyValue $command @("startedAt", "runAt", "capturedAt")) "commands.startedAt" $cutoff $now
    Assert-RecentDate (Get-ObjectPropertyValue $command @("completedAt", "finishedAt", "capturedAt")) "commands.completedAt" $cutoff $now
}

$artifacts = @(Get-ObjectPropertyValue $transcript @("artifacts", "evidenceArtifacts"))
if ($artifacts.Count -eq 0 -or $null -eq $artifacts[0]) {
    throw "artifacts must include at least one sanitized evidence artifact"
}

$combinedEvidenceText = $raw
foreach ($artifact in $artifacts) {
    Assert-RequiredText (Get-ObjectPropertyValue $artifact @("id", "name")) "artifacts.id"
    Assert-RequiredText (Get-ObjectPropertyValue $artifact @("description", "purpose")) "artifacts.description"
    if (-not (Test-PassingStatus (Get-ObjectPropertyValue $artifact @("status", "result", "outcome")))) {
        throw "Every artifact must have a passing status/result/outcome"
    }
    if (-not (Test-TrueValue (Get-ObjectPropertyValue $artifact @("sanitized", "redacted")))) {
        throw "Every artifact must declare sanitized=true"
    }
    if (-not (Test-FalseValue (Get-ObjectPropertyValue $artifact @("containsSecrets", "secretsPresent", "hasSecrets")))) {
        throw "Every artifact must declare containsSecrets=false"
    }

    Assert-RecentDate (Get-ObjectPropertyValue $artifact @("capturedAt", "generatedAt", "completedAt", "runAt")) "artifacts.capturedAt" $cutoff $now

    $reference = Get-ObjectPropertyValue $artifact @("ref", "path", "file", "uri", "url", "artifact")
    Assert-RequiredText $reference "artifacts.ref"
    $localText = Assert-LocalEvidenceFile ([string] $reference) $evidenceRootFullPath
    if (-not [string]::IsNullOrWhiteSpace($localText)) {
        $combinedEvidenceText = "$combinedEvidenceText`n$localText"
    }
}

$typeRequirements = @{
    "live-load" = @("phase3-high-volume-load\.ps1", "(?i)\bRequireLive\b", "(?i)\bEvidenceOutputPath\b", "(?i)\bgate\b.{0,40}\bPASS\b")
    "restore-drill" = @("backup-restore\.ps1", "(?i)\brestore\b", "synthetic-smoke\.ps1", "(?i)\bpostgres\b", "(?i)\bclickhouse\b", "(?i)\bminio\b")
    "security-scan" = @("(?i)\bgitleaks\b", "(?i)\btrivy\b", "(?i)\bsbom\b|software bill of materials", "(?i)\bnpm audit\b")
    "monitoring-handoff" = @("(?i)\balertmanager\b", "platform-pager", "platform-primary", "(?i)\bkubectl\s+kustomize\b|promtool")
}

foreach ($pattern in $typeRequirements[$Type]) {
    if ($combinedEvidenceText -notmatch $pattern) {
        throw "Evidence transcript type '$Type' is missing required evidence pattern: $pattern"
    }
}

Write-Host "Evidence transcript validation passed for $Type`: $transcriptFullPath"
