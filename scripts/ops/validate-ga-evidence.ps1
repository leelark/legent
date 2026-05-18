param(
    [string] $EvidenceDir,
    [string] $ManifestPath,
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
        "(?i)<\s*(todo|tbd|[^>\r\n]*(token|secret|file|path|date|owner|digest|url)[^>\r\n]*)\s*>",
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

function Convert-ToJsonText($Value) {
    if ($null -eq $Value) {
        return ""
    }

    if ($Value -is [string]) {
        return $Value
    }

    return ($Value | ConvertTo-Json -Depth 20 -Compress)
}

function Normalize-Token([string] $Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }

    return (($Value.ToLowerInvariant()) -replace "[^a-z0-9]", "")
}

function Add-PathCandidate($Value, [System.Collections.Generic.List[string]] $Paths) {
    if ($null -eq $Value) {
        return
    }

    if ($Value -is [string]) {
        if (-not [string]::IsNullOrWhiteSpace($Value)) {
            $Paths.Add($Value)
        }
        return
    }

    if ($Value -is [System.Collections.IEnumerable]) {
        foreach ($item in $Value) {
            Add-PathCandidate $item $Paths
        }
        return
    }

    $nestedValue = Get-ObjectPropertyValue $Value @("path", "file", "evidenceFile", "transcript", "artifact")
    if ($null -ne $nestedValue) {
        Add-PathCandidate $nestedValue $Paths
    }
}

function Get-RecordPaths($Record) {
    $paths = New-Object System.Collections.Generic.List[string]

    if ($Record -is [string]) {
        Add-PathCandidate $Record $paths
        return @($paths)
    }

    foreach ($field in @("path", "file", "files", "paths", "evidenceFile", "evidenceFiles", "transcript", "transcripts", "artifact", "artifacts", "sbom", "checklist")) {
        $value = Get-ObjectPropertyValue $Record @($field)
        if ($null -ne $value) {
            Add-PathCandidate $value $paths
        }
    }

    return @($paths | Select-Object -Unique)
}

function Get-RecordType([string] $DeclaredType, $Record) {
    if ($Record -isnot [string]) {
        $typeValue = Get-ObjectPropertyValue $Record @("type", "category", "id", "name", "artifactType")
        if ($null -ne $typeValue -and -not [string]::IsNullOrWhiteSpace([string] $typeValue)) {
            return [string] $typeValue
        }
    }

    return $DeclaredType
}

function Get-RecordStatus($Record) {
    if ($Record -is [string]) {
        return $null
    }

    $statusValue = Get-ObjectPropertyValue $Record @("status", "result", "outcome", "gate", "state")
    if ($null -eq $statusValue) {
        return $null
    }

    return [string] $statusValue
}

function Get-RecordDate($Record, $Manifest) {
    if ($Record -isnot [string]) {
        $dateValue = Get-ObjectPropertyValue $Record @("date", "timestamp", "capturedAt", "generatedAt", "completedAt", "runAt", "verifiedAt", "signedAt")
        if ($null -ne $dateValue -and -not [string]::IsNullOrWhiteSpace([string] $dateValue)) {
            return [string] $dateValue
        }
    }

    $manifestDate = Get-ObjectPropertyValue $Manifest @("date", "timestamp", "capturedAt", "generatedAt", "completedAt", "runAt", "verifiedAt")
    if ($null -ne $manifestDate -and -not [string]::IsNullOrWhiteSpace([string] $manifestDate)) {
        return [string] $manifestDate
    }

    return $null
}

function Parse-EvidenceDate([string] $DateValue, [string] $ArtifactId) {
    if ([string]::IsNullOrWhiteSpace($DateValue)) {
        throw "GA evidence artifact '$ArtifactId' does not declare a date/timestamp"
    }

    $parsed = [datetimeoffset]::MinValue
    $style = [System.Globalization.DateTimeStyles]::AssumeLocal
    if (-not [datetimeoffset]::TryParse($DateValue, [System.Globalization.CultureInfo]::InvariantCulture, $style, [ref] $parsed)) {
        throw "GA evidence artifact '$ArtifactId' has an invalid date/timestamp: $DateValue"
    }

    return $parsed
}

function Add-ArtifactRecord(
    [System.Collections.Generic.List[object]] $Records,
    [string] $DeclaredType,
    $Record,
    [string] $Source
) {
    if ($null -eq $Record) {
        return
    }

    if ($Record -is [string]) {
        $Records.Add([pscustomobject]@{
                Type   = $DeclaredType
                Record = $Record
                Source = $Source
            })
        return
    }

    if ($Record -is [System.Collections.IEnumerable]) {
        $index = 0
        foreach ($item in $Record) {
            Add-ArtifactRecord $Records $DeclaredType $item "$Source[$index]"
            $index++
        }
        return
    }

    $Records.Add([pscustomobject]@{
            Type   = Get-RecordType $DeclaredType $Record
            Record = $Record
            Source = $Source
        })
}

function Get-ArtifactRecords($Manifest) {
    $records = New-Object System.Collections.Generic.List[object]

    $evidence = Get-ObjectPropertyValue $Manifest @("evidence")
    if ($null -ne $evidence) {
        foreach ($property in $evidence.PSObject.Properties) {
            Add-ArtifactRecord $records $property.Name $property.Value "evidence.$($property.Name)"
        }
    }

    $artifacts = Get-ObjectPropertyValue $Manifest @("artifacts")
    if ($null -ne $artifacts) {
        Add-ArtifactRecord $records "artifact" $artifacts "artifacts"
    }

    foreach ($property in $Manifest.PSObject.Properties) {
        if ($property.Name -in @("evidence", "artifacts", "releaseVersion", "environment", "date", "timestamp", "capturedAt", "generatedAt", "completedAt", "runAt", "verifiedAt", "owner", "notes")) {
            continue
        }
        Add-ArtifactRecord $records $property.Name $property.Value $property.Name
    }

    return $records.ToArray()
}

function Test-ArtifactMatchesSpec($Artifact, $Spec) {
    $recordText = Convert-ToJsonText $Artifact.Record
    $haystack = Normalize-Token "$($Artifact.Type) $($Artifact.Source) $recordText"
    foreach ($alias in $Spec.Aliases) {
        if ($haystack.Contains((Normalize-Token $alias))) {
            return $true
        }
    }

    return $false
}

function Read-EvidenceText([string] $Path) {
    $fileInfo = Get-Item -LiteralPath $Path
    if ($fileInfo.Length -gt 5242880) {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            $buffer = New-Object byte[] 5242880
            $read = $stream.Read($buffer, 0, $buffer.Length)
            return [System.Text.Encoding]::UTF8.GetString($buffer, 0, $read)
        } finally {
            $stream.Dispose()
        }
    }

    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
}

function Assert-ArtifactEvidence($Artifact, $Spec, [string] $EvidenceRoot, $Manifest, [datetime] $Cutoff, [datetime] $Now) {
    $recordText = Convert-ToJsonText $Artifact.Record
    $placeholder = Get-PlaceholderMatch $recordText
    if ($null -ne $placeholder) {
        throw "GA evidence artifact '$($Spec.Id)' contains placeholder text '$placeholder' in manifest record $($Artifact.Source)"
    }

    $status = Get-RecordStatus $Artifact.Record
    if ([string]::IsNullOrWhiteSpace($status)) {
        throw "GA evidence artifact '$($Spec.Id)' must declare status/result/outcome"
    }
    if ($status -notmatch "^(?i:pass|passed|success|succeeded|complete|completed|verified|approved|ok)$") {
        throw "GA evidence artifact '$($Spec.Id)' is not passing: status '$status'"
    }

    $artifactDate = Parse-EvidenceDate (Get-RecordDate $Artifact.Record $Manifest) $Spec.Id
    $artifactLocalDate = $artifactDate.LocalDateTime
    if ($artifactLocalDate -lt $Cutoff) {
        throw "GA evidence artifact '$($Spec.Id)' is stale: $artifactDate is older than $MaxAgeDays days"
    }
    if ($artifactLocalDate -gt $Now.AddDays(1)) {
        throw "GA evidence artifact '$($Spec.Id)' is dated in the future: $artifactDate"
    }

    $paths = @(Get-RecordPaths $Artifact.Record)
    if ($paths.Count -eq 0) {
        throw "GA evidence artifact '$($Spec.Id)' must reference at least one local evidence file"
    }

    $combinedEvidenceText = "$($Artifact.Type)`n$($Artifact.Source)`n$recordText"
    foreach ($path in $paths) {
        if (Test-SecretLikePath $path) {
            throw "GA evidence artifact '$($Spec.Id)' references a secret/env-like path: $path"
        }

        $fullPath = Get-FullPath $path $EvidenceRoot
        if (-not (Test-IsPathUnderRoot $fullPath $EvidenceRoot)) {
            throw "GA evidence artifact '$($Spec.Id)' references a path outside the evidence directory: $path"
        }

        if (Test-SecretLikePath $fullPath) {
            throw "GA evidence artifact '$($Spec.Id)' resolves to a secret/env-like path: $fullPath"
        }

        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            throw "GA evidence artifact '$($Spec.Id)' references a missing evidence file: $path"
        }

        $fileText = Read-EvidenceText $fullPath
        $filePlaceholder = Get-PlaceholderMatch $fileText
        if ($null -ne $filePlaceholder) {
            throw "GA evidence artifact '$($Spec.Id)' evidence file '$path' contains placeholder text '$filePlaceholder'"
        }

        $combinedEvidenceText = "$combinedEvidenceText`n$fileText"
    }

    foreach ($pattern in $Spec.RequiredPatterns) {
        if ($combinedEvidenceText -notmatch $pattern) {
            throw "GA evidence artifact '$($Spec.Id)' is missing required evidence pattern: $pattern"
        }
    }

    if ($Spec.Id -eq "live-load" -and $combinedEvidenceText -match "(?i)\bdry[-\s]?run\b|synthetic\s+dataset|\bmock\b|RequireLive\s*[:=]\s*false|without\s+-RequireLive") {
        throw "GA evidence artifact 'live-load' appears to be dry-run, mock, or synthetic evidence"
    }
}

if ([string]::IsNullOrWhiteSpace($EvidenceDir) -and [string]::IsNullOrWhiteSpace($ManifestPath)) {
    throw "EvidenceDir or ManifestPath is required"
}

$evidenceRoot = $null
$manifestFullPath = $null

if (-not [string]::IsNullOrWhiteSpace($EvidenceDir)) {
    $candidateEvidencePath = Get-FullPath $EvidenceDir $repoRoot
    if (Test-Path -LiteralPath $candidateEvidencePath -PathType Leaf) {
        if (-not [string]::IsNullOrWhiteSpace($ManifestPath)) {
            throw "EvidenceDir points to a file. Use EvidenceDir as a directory or omit ManifestPath."
        }
        $manifestFullPath = $candidateEvidencePath
        $evidenceRoot = Split-Path -Parent $candidateEvidencePath
    } elseif (Test-Path -LiteralPath $candidateEvidencePath -PathType Container) {
        $evidenceRoot = $candidateEvidencePath
    } else {
        throw "Evidence directory not found: $EvidenceDir"
    }
}

if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $manifestCandidates = @(
        "ga-evidence-manifest.json",
        "evidence-manifest.json",
        "manifest.json"
    )
    foreach ($candidate in $manifestCandidates) {
        $candidateManifest = Join-Path $evidenceRoot $candidate
        if (Test-Path -LiteralPath $candidateManifest -PathType Leaf) {
            $manifestFullPath = $candidateManifest
            break
        }
    }

    if ([string]::IsNullOrWhiteSpace($manifestFullPath)) {
        throw "GA evidence manifest not found in $evidenceRoot. Expected one of: $($manifestCandidates -join ', ')"
    }
} else {
    $manifestBase = if ($null -ne $evidenceRoot) { $evidenceRoot } else { $repoRoot }
    $manifestFullPath = Get-FullPath $ManifestPath $manifestBase
    if (-not (Test-Path -LiteralPath $manifestFullPath -PathType Leaf)) {
        throw "GA evidence manifest not found: $ManifestPath"
    }
    if ($null -eq $evidenceRoot) {
        $evidenceRoot = Split-Path -Parent $manifestFullPath
    }
}

if (Test-SecretLikePath $evidenceRoot) {
    throw "Evidence directory looks like a secret/env path: $evidenceRoot"
}

if (Test-SecretLikePath $manifestFullPath) {
    throw "GA evidence manifest path looks like a secret/env path: $manifestFullPath"
}

if ([System.IO.Path]::GetExtension($manifestFullPath).ToLowerInvariant() -ne ".json") {
    throw "GA evidence manifest must be JSON: $manifestFullPath"
}

$manifestRaw = Get-Content -Raw -Encoding UTF8 -LiteralPath $manifestFullPath
$manifestPlaceholder = Get-PlaceholderMatch $manifestRaw
if ($null -ne $manifestPlaceholder) {
    throw "GA evidence manifest contains placeholder text '$manifestPlaceholder'"
}

$manifest = $manifestRaw | ConvertFrom-Json
$artifactRecords = @(Get-ArtifactRecords $manifest)
if ($artifactRecords.Count -eq 0) {
    throw "GA evidence manifest does not contain evidence artifacts"
}

$requiredArtifacts = @(
    @{ Id = "synthetic-smoke"; DisplayName = "Synthetic smoke"; Aliases = @("synthetic-smoke", "synthetic smoke", "syntheticsmoke"); RequiredPatterns = @() },
    @{ Id = "live-load"; DisplayName = "Live load"; Aliases = @("live-load", "live load", "high-volume-load", "phase3-high-volume-load"); RequiredPatterns = @("(?i)\bRequireLive\b") },
    @{ Id = "restore-drill"; DisplayName = "Restore drill"; Aliases = @("restore-drill", "restore drill", "backup restore", "backup-restore"); RequiredPatterns = @("(?i)\brestore\b") },
    @{ Id = "ci-gitleaks"; DisplayName = "CI security gitleaks"; Aliases = @("ci-gitleaks", "gitleaks", "ci security gitleaks"); RequiredPatterns = @("(?i)\bgitleaks\b") },
    @{ Id = "ci-trivy"; DisplayName = "CI security Trivy"; Aliases = @("ci-trivy", "trivy", "ci security trivy"); RequiredPatterns = @("(?i)\btrivy\b") },
    @{ Id = "ci-sbom"; DisplayName = "CI security SBOM"; Aliases = @("ci-sbom", "sbom", "software bill of materials"); RequiredPatterns = @("(?i)\bsbom\b|software bill of materials") },
    @{ Id = "monitoring-alert-routing"; DisplayName = "Monitoring alert routing"; Aliases = @("monitoring-alert-routing", "alert routing", "alertmanager", "monitoring"); RequiredPatterns = @("platform-pager", "platform-primary") },
    @{ Id = "tls-cert-ownership"; DisplayName = "TLS/cert ownership"; Aliases = @("tls-cert-ownership", "tls ownership", "cert ownership", "certificate ownership", "legent-public-tls"); RequiredPatterns = @("legent-public-tls") },
    @{ Id = "restricted-admission"; DisplayName = "Restricted admission"; Aliases = @("restricted-admission", "pod security admission", "restricted pod security", "restricted admission"); RequiredPatterns = @("(?i)\brestricted\b") },
    @{ Id = "registry-digest"; DisplayName = "Registry digest"; Aliases = @("registry-digest", "image digest", "digest evidence"); RequiredPatterns = @("sha256:[a-f0-9]{64}") },
    @{ Id = "registry-sbom"; DisplayName = "Registry image SBOM"; Aliases = @("registry-sbom", "image sbom", "registry image sbom", "software bill of materials"); RequiredPatterns = @("(?i)\b(sbom|software bill of materials)\b", "sha256:[a-f0-9]{64}") },
    @{ Id = "registry-signature"; DisplayName = "Registry signature"; Aliases = @("registry-signature", "image signature", "cosign", "signature evidence"); RequiredPatterns = @("(?i)\b(signature|cosign)\b") },
    @{ Id = "registry-provenance"; DisplayName = "Registry provenance"; Aliases = @("registry-provenance", "image provenance", "attestation", "slsa", "provenance evidence"); RequiredPatterns = @("(?i)\b(provenance|attestation|slsa)\b") }
)

$now = Get-Date
$cutoff = $now.AddDays(-$MaxAgeDays)
$validatedArtifacts = New-Object System.Collections.Generic.List[string]

foreach ($spec in $requiredArtifacts) {
    $matches = @($artifactRecords | Where-Object { Test-ArtifactMatchesSpec $_ $spec })
    if ($matches.Count -eq 0) {
        throw "GA evidence manifest is missing required artifact '$($spec.Id)' ($($spec.DisplayName))"
    }

    foreach ($match in $matches) {
        Assert-ArtifactEvidence $match $spec $evidenceRoot $manifest $cutoff $now
    }

    $validatedArtifacts.Add($spec.Id)
}

Write-Host "GA evidence validation passed for $($validatedArtifacts.Count) required artifacts in $evidenceRoot"
