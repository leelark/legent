param(
    [string] $OverlayPath = "infrastructure/kubernetes/overlays/production",
    [Parameter(Mandatory = $true)] [string] $ManifestPath,
    [string] $EvidenceRoot,
    [int] $MaxAgeDays = 14,
    [switch] $AllowTemplatePlaceholders
)

$ErrorActionPreference = "Stop"

if ($MaxAgeDays -lt 1) {
    throw "MaxAgeDays must be at least 1"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Resolve-RepoPath {
    param(
        [string] $Path,
        [string] $BasePath = $repoRoot
    )

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $BasePath $Path))
}

function Get-ObjectPropertyValue {
    param(
        $InputObject,
        [Parameter(Mandatory = $true)] [string[]] $Names
    )

    if ($null -eq $InputObject) {
        return $null
    }

    foreach ($name in $Names) {
        $property = $InputObject.PSObject.Properties[$name]
        if ($null -ne $property) {
            return $property.Value
        }
    }

    return $null
}

function Test-HasText {
    param($Value)

    return ($Value -is [string] -and -not [string]::IsNullOrWhiteSpace($Value))
}

function Test-Verified {
    param($Value)

    if ($Value -is [bool]) {
        return $Value
    }

    if ($Value -is [string]) {
        return @("true", "verified", "pass", "passed", "success", "succeeded") -contains $Value.Trim().ToLowerInvariant()
    }

    return $false
}

function Get-ImageDigest {
    param([string] $Image)

    if ($Image -match "@(?<digest>sha256:[a-f0-9]{64})$") {
        return $Matches["digest"]
    }

    return $null
}

function Get-PlaceholderMatch {
    param([string] $Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }

    foreach ($pattern in @(
            "(?i)\bTBD\b",
            "(?i)\bTODO\b",
            "(?i)\bFIXME\b",
            "(?i)\bCHANGEME\b",
            "(?i)\bREPLACE_ME\b",
            "(?i)\breplace_with_[a-z0-9_]+\b",
            "(?i)\byour_[a-z0-9_]+\b",
            "(?i)<[^>\r\n]+>",
            "(?i)\bexample\.(com|org|net)\b",
            "(?i)\bplaceholder\b"
        )) {
        $match = [regex]::Match($Text, $pattern)
        if ($match.Success) {
            return $match.Value
        }
    }

    return $null
}

function Test-SecretLikePath {
    param([string] $Path)

    $normalized = $Path -replace "/", "\"
    $leaf = ([System.IO.Path]::GetFileName($normalized)).ToLowerInvariant()
    if ($leaf -match "^\.env($|\.)" -or $leaf -match "\.(pem|key|p12|pfx|jks|keystore)$") {
        return $true
    }

    if ($leaf -match "^(secrets?|credentials?|id_rsa|id_dsa|id_ecdsa|id_ed25519|kubeconfig)(\..*)?$") {
        return $true
    }

    foreach ($segment in @($normalized -split "\\+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })) {
        if ($segment.ToLowerInvariant() -in @(".env", ".ssh", "secrets", "credentials")) {
            return $true
        }
    }

    return $false
}

function Test-IsPathUnderRoot {
    param(
        [string] $Path,
        [string] $Root
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path).TrimEnd("\", "/")
    $fullRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd("\", "/")
    if ([string]::Equals($fullPath, $fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }

    return $fullPath.StartsWith("$fullRoot$([System.IO.Path]::DirectorySeparatorChar)", [System.StringComparison]::OrdinalIgnoreCase)
}

function Assert-RequiredText {
    param(
        [string] $Path,
        $Value
    )

    if ($AllowTemplatePlaceholders) {
        return
    }

    if (-not (Test-HasText $Value)) {
        throw "$Path is required"
    }

    $placeholder = Get-PlaceholderMatch ([string] $Value)
    if ($null -ne $placeholder) {
        throw "$Path contains placeholder text '$placeholder'"
    }
}

function Assert-RecentDate {
    param(
        [string] $Path,
        $Value
    )

    if ($AllowTemplatePlaceholders) {
        return
    }

    Assert-RequiredText $Path $Value

    $parsed = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParse([string] $Value, [System.Globalization.CultureInfo]::InvariantCulture, [System.Globalization.DateTimeStyles]::AssumeLocal, [ref] $parsed)) {
        throw "$Path must be a valid date/timestamp"
    }

    $now = Get-Date
    if ($parsed.LocalDateTime -lt $now.AddDays(-$MaxAgeDays)) {
        throw "$Path is stale: $parsed is older than $MaxAgeDays days"
    }

    if ($parsed.LocalDateTime -gt $now.AddDays(1)) {
        throw "$Path is dated in the future: $parsed"
    }
}

function Get-EvidenceReference {
    param($Evidence)

    if ($Evidence -is [string]) {
        return $Evidence
    }

    return Get-ObjectPropertyValue $Evidence @("transcript", "transcriptPath", "path", "uri", "url", "artifact", "artifactUri", "log", "command", "summary")
}

function Assert-EvidenceReference {
    param(
        [string] $Path,
        $Value,
        [string] $Root
    )

    Assert-RequiredText $Path $Value

    if ($AllowTemplatePlaceholders -or -not (Test-HasText $Value)) {
        return
    }

    $reference = [string] $Value
    if (Test-SecretLikePath $reference) {
        throw "$Path references a secret/env-like path: $reference"
    }

    if ($reference -match "^[a-z][a-z0-9+.-]*://") {
        return
    }

    if ([string]::IsNullOrWhiteSpace($Root)) {
        return
    }

    $fullPath = Resolve-RepoPath $reference $Root
    if (-not (Test-IsPathUnderRoot $fullPath $Root)) {
        throw "$Path references a path outside EvidenceRoot: $reference"
    }

    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "$Path references a missing evidence file: $reference"
    }

    $fileText = Get-Content -LiteralPath $fullPath -Raw -Encoding UTF8
    $placeholder = Get-PlaceholderMatch $fileText
    if ($null -ne $placeholder) {
        throw "$Path evidence file '$reference' contains placeholder text '$placeholder'"
    }
}

function Assert-SbomEvidence {
    param(
        [string] $Image,
        $Entry,
        [string] $Root
    )

    $sbom = Get-ObjectPropertyValue $Entry @("sbom", "sbomEvidence")
    if ($null -eq $sbom) {
        throw "Image evidence manifest entry for $Image is missing SBOM evidence"
    }

    $reference = Get-EvidenceReference $sbom
    Assert-EvidenceReference "images[$Image].sbom.reference" $reference $Root

    $digest = Get-ObjectPropertyValue $sbom @("digest", "sbomDigest")
    if ($AllowTemplatePlaceholders -and -not (Test-HasText $digest)) {
        return
    }

    Assert-RequiredText "images[$Image].sbom.digest" $digest
    if ((Test-HasText $digest) -and $digest -notmatch "^sha256:[a-f0-9]{64}$") {
        throw "Image evidence manifest entry for $Image has invalid SBOM digest $digest"
    }
}

function Assert-VerificationEvidence {
    param(
        [string] $Image,
        $Entry,
        [string] $Kind,
        [string[]] $PropertyNames,
        [string] $Root
    )

    $evidence = Get-ObjectPropertyValue $Entry $PropertyNames
    if ($null -eq $evidence) {
        throw "Image evidence manifest entry for $Image is missing $Kind evidence"
    }

    $verified = Get-ObjectPropertyValue $evidence @("verified", "verificationPassed", "passed")
    if (-not $AllowTemplatePlaceholders -and -not (Test-Verified $verified)) {
        throw "Image evidence manifest entry for $Image must mark $Kind verification as verified"
    }

    $reference = Get-EvidenceReference $evidence
    Assert-EvidenceReference "images[$Image].$Kind.reference" $reference $Root

    if ($Kind -eq "signature") {
        Assert-RequiredText "images[$Image].signature.verifier" (Get-ObjectPropertyValue $evidence @("verifier", "issuer", "identity"))
    }

    if ($Kind -eq "provenance") {
        Assert-RequiredText "images[$Image].provenance.builderId" (Get-ObjectPropertyValue $evidence @("builderId", "builder"))
        Assert-RequiredText "images[$Image].provenance.predicateType" (Get-ObjectPropertyValue $evidence @("predicateType", "predicate"))
    }
}

$manifestFullPath = Resolve-RepoPath $ManifestPath
if (-not (Test-Path -LiteralPath $manifestFullPath -PathType Leaf)) {
    throw "Image evidence manifest not found: $ManifestPath"
}

$evidenceFullRoot = $null
if (-not [string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $evidenceFullRoot = Resolve-RepoPath $EvidenceRoot
    if (-not (Test-Path -LiteralPath $evidenceFullRoot -PathType Container)) {
        throw "EvidenceRoot not found: $EvidenceRoot"
    }
    if (Test-SecretLikePath $evidenceFullRoot) {
        throw "EvidenceRoot looks like a secret/env path: $evidenceFullRoot"
    }
}

Push-Location $repoRoot
try {
    $rendered = (kubectl kustomize $OverlayPath) -join "`n"
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl kustomize $OverlayPath failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$renderedImages = @([regex]::Matches($rendered, "(?m)^\s*image:\s+(legent/[^\s]+)\s*$") | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
if ($renderedImages.Count -eq 0) {
    throw "Production overlay rendered no legent images"
}

$manifestRaw = Get-Content -LiteralPath $manifestFullPath -Raw -Encoding UTF8
if (-not $AllowTemplatePlaceholders) {
    $manifestPlaceholder = Get-PlaceholderMatch $manifestRaw
    if ($null -ne $manifestPlaceholder) {
        throw "Image evidence manifest contains placeholder text '$manifestPlaceholder'"
    }
}

$manifest = $manifestRaw | ConvertFrom-Json
$schemaVersion = Get-ObjectPropertyValue $manifest @("schemaVersion")
if ($schemaVersion -ne "legent.image-evidence.v1") {
    throw "Image evidence manifest schemaVersion must be legent.image-evidence.v1"
}

Assert-RecentDate "generatedAt" (Get-ObjectPropertyValue $manifest @("generatedAt", "verifiedAt", "completedAt"))

$manifestImages = Get-ObjectPropertyValue $manifest @("images")
if ($null -eq $manifestImages) {
    throw "Image evidence manifest must contain an images array"
}

$entries = @($manifestImages)
if ($entries.Count -eq 0) {
    throw "Image evidence manifest images array must not be empty"
}

$entryByImage = @{}
foreach ($entry in $entries) {
    $image = Get-ObjectPropertyValue $entry @("image", "renderedImage", "imageReference")
    Assert-RequiredText "images[].image" $image
    if (-not (Test-HasText $image)) {
        continue
    }
    if ($entryByImage.ContainsKey($image)) {
        throw "Image evidence manifest contains duplicate entry for $image"
    }
    $entryByImage[$image] = $entry
}

$renderedSet = @{}
foreach ($image in $renderedImages) {
    $renderedSet[$image] = $true
}

$missing = @($renderedImages | Where-Object { -not $entryByImage.ContainsKey($_) })
if ($missing.Count -gt 0) {
    throw "Image evidence manifest is missing rendered legent images: $($missing -join ', ')"
}

$extra = @($entryByImage.Keys | Where-Object { -not $renderedSet.ContainsKey($_) } | Sort-Object)
if ($extra.Count -gt 0) {
    throw "Image evidence manifest contains stale/non-rendered legent images: $($extra -join ', ')"
}

foreach ($image in $renderedImages) {
    $entry = $entryByImage[$image]
    $renderedDigest = Get-ImageDigest $image
    if (-not $AllowTemplatePlaceholders -and -not (Test-HasText $renderedDigest)) {
        throw "Image evidence requires digest-pinned rendered images; tag-only image found: $image"
    }

    $manifestDigest = Get-ObjectPropertyValue $entry @("digest", "imageDigest", "renderedDigest")
    if ($AllowTemplatePlaceholders -and -not (Test-HasText $manifestDigest)) {
        Assert-SbomEvidence -Image $image -Entry $entry -Root $evidenceFullRoot
        Assert-VerificationEvidence -Image $image -Entry $entry -Kind "signature" -PropertyNames @("signature", "signatureVerification") -Root $evidenceFullRoot
        Assert-VerificationEvidence -Image $image -Entry $entry -Kind "provenance" -PropertyNames @("provenance", "provenanceVerification") -Root $evidenceFullRoot
        continue
    }

    Assert-RequiredText "images[$image].digest" $manifestDigest
    if ((Test-HasText $manifestDigest) -and $manifestDigest -notmatch "^sha256:[a-f0-9]{64}$") {
        throw "Image evidence manifest entry for $image has invalid digest $manifestDigest"
    }

    if ((Test-HasText $renderedDigest) -and $manifestDigest -ne $renderedDigest) {
        throw "Image evidence manifest digest for $image does not match rendered image digest"
    }

    Assert-SbomEvidence -Image $image -Entry $entry -Root $evidenceFullRoot
    Assert-VerificationEvidence -Image $image -Entry $entry -Kind "signature" -PropertyNames @("signature", "signatureVerification") -Root $evidenceFullRoot
    Assert-VerificationEvidence -Image $image -Entry $entry -Kind "provenance" -PropertyNames @("provenance", "provenanceVerification") -Root $evidenceFullRoot
}

Write-Host "Image evidence manifest validation passed for $($renderedImages.Count) rendered legent images"
