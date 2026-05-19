param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [string]$EvidenceRoot,
    [switch]$RequireDigests,
    [string]$KustomizationPath = "infrastructure/kubernetes/overlays/production/kustomization.yml"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

if (-not (Test-Path $ManifestPath)) { Fail "Missing image evidence manifest: $ManifestPath" }
if (-not $EvidenceRoot) { $EvidenceRoot = Split-Path -Parent $ManifestPath }
$resolvedRoot = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $EvidenceRoot).Path)

function Test-ContainedPath([string]$Root, [string]$Candidate) {
    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $candidateFull = [System.IO.Path]::GetFullPath($Candidate)
    return $candidateFull.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)
}

function Resolve-EvidencePath([string]$Root, [string]$Value, [string]$Field, [string]$Image) {
    if ([System.IO.Path]::IsPathRooted($Value)) {
        Fail "Absolute evidence paths are not allowed for $Image ${Field}: $Value"
    }
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $Root $Value))
    if (-not (Test-ContainedPath $Root $candidate)) {
        Fail "Evidence path escapes evidence root for $Image ${Field}: $Value"
    }
    if (-not (Test-Path $candidate)) {
        Fail "Image evidence file missing for $Image ${Field}: $candidate"
    }
}

function Get-KustomizationImages([string]$Path) {
    $result = @{}
    if (-not (Test-Path $Path)) { return $result }
    $current = $null
    foreach ($line in (Get-Content -Path $Path)) {
        if ($line -match '^\s*-\s+name:\s*"?([^"\s]+)"?\s*$') {
            $current = $Matches[1]
            if (-not $result.ContainsKey($current)) {
                $result[$current] = [ordered]@{ image = $current; digest = $null; tag = $null }
            }
            continue
        }
        if ($current -and $line -match '^\s*digest:\s*"?([^"\s]+)"?\s*$') {
            $result[$current].digest = $Matches[1]
            continue
        }
        if ($current -and $line -match '^\s*newTag:\s*"?([^"\s]+)"?\s*$') {
            $result[$current].tag = $Matches[1]
            continue
        }
    }
    return $result
}

$manifest = Get-Content -Path $ManifestPath -Raw | ConvertFrom-Json
$images = @($manifest.images)
if ($images.Count -eq 0) { Fail "Image evidence manifest has no images." }
$manifestByImage = @{}

foreach ($image in $images) {
    if (-not $image.image) { Fail "Image entry missing image field." }
    if ($manifestByImage.ContainsKey([string]$image.image)) { Fail "Duplicate image evidence entry for $($image.image)." }
    $manifestByImage[[string]$image.image] = $image
    if ($RequireDigests -and ([string]$image.digest -notmatch "^sha256:[a-fA-F0-9]{64}$")) {
        Fail "Image evidence digest is missing or invalid for $($image.image)"
    }
    foreach ($field in @("reviewedBy", "reviewedAt")) {
        $value = [string]$image.$field
        if (-not $value -or $value -match "replace-with|placeholder|TODO|YYYY") {
            Fail "Image evidence field $field is missing or placeholder for $($image.image)"
        }
    }
    foreach ($field in @("sbom", "sbomDigest", "signatureEvidence", "provenanceEvidence", "builderId", "predicateType")) {
        $value = [string]$image.$field
        if (-not $value -or $value -match "replace-with|placeholder|TODO") {
            Fail "Image evidence field $field is missing or placeholder for $($image.image)"
        }
    }
    foreach ($field in @("signatureEvidence", "provenanceEvidence")) {
        $value = [string]$image.$field
        if ($EvidenceRoot -and $value -and -not ($value -match "^[a-z]+://")) {
            Resolve-EvidencePath $resolvedRoot $value $field $image.image
        }
    }
}

$productionImages = Get-KustomizationImages $KustomizationPath
if ($productionImages.Count -gt 0) {
    foreach ($name in $productionImages.Keys) {
        if (-not $manifestByImage.ContainsKey($name)) {
            Fail "Image evidence missing production image: $name"
        }
        $productionDigest = [string]$productionImages[$name].digest
        if ($RequireDigests) {
            if ($productionDigest -notmatch "^sha256:[a-fA-F0-9]{64}$") {
                Fail "Production image $name is not digest-pinned in $KustomizationPath"
            }
            if ([string]$manifestByImage[$name].digest -ne $productionDigest) {
                Fail "Image evidence digest for $name does not match production kustomization digest."
            }
        }
    }
    foreach ($name in $manifestByImage.Keys) {
        if (-not $productionImages.ContainsKey($name)) {
            Fail "Image evidence contains image not present in production kustomization: $name"
        }
    }
}

Write-Host "Image evidence validation passed for $($images.Count) images."
