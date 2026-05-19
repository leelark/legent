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
}

$manifest = Get-Content -Path $ManifestPath -Raw | ConvertFrom-Json
$required = @(
    "syntheticSmoke",
    "liveLoad",
    "restoreDrill",
    "ciSecurityTranscript",
    "filesystemSbom",
    "monitoringHandoff",
    "tlsCertificate",
    "restrictedAdmission",
    "registryImageEvidence"
)

foreach ($field in $required) {
    $value = [string]$manifest.$field
    if (-not $value -or $value -match "replace-with|placeholder|TODO") {
        Fail "GA evidence field missing or placeholder: $field"
    }
    Resolve-EvidencePath $resolvedEvidenceDir $value $field
}

Write-Host "GA evidence validation passed."
