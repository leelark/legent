param(
    [string] $OverlayPath = "infrastructure/kubernetes/overlays/production",
    [string] $OutputPath = "legent-image-supply-chain-checklist.md",
    [string] $ManifestOutputPath
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Resolve-OutputPath {
    param([string] $Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }

    return (Join-Path $repoRoot $Path)
}

function Get-ImageDigest {
    param([string] $Image)

    if ($Image -match "@(?<digest>sha256:[a-f0-9]{64})$") {
        return $Matches["digest"]
    }

    return $null
}

function Write-TextFile {
    param(
        [Parameter(Mandatory = $true)] [string] $Path,
        [Parameter(Mandatory = $true)] [string] $Content
    )

    $outputDirectory = Split-Path -Parent $Path
    if ($outputDirectory -and -not (Test-Path $outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory | Out-Null
    }

    Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
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

$images = @([regex]::Matches($rendered, "(?m)^\s*image:\s+([^\s]+)\s*$") | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
$legentImages = @($images | Where-Object { $_ -like "legent/*" })

if ($legentImages.Count -eq 0) {
    throw "Production overlay rendered no legent images"
}

$generatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

$lines = @(
    "# Image Supply-Chain Evidence Checklist",
    "",
    "Generated: $generatedAt",
    ("Overlay: ``{0}``" -f $OverlayPath),
    "",
    "This checklist is generated from the rendered production overlay. It does not prove registry state by itself; attach registry-backed digest, signature, SBOM, and provenance evidence for each image before promotion.",
    "Use ``-ManifestOutputPath`` to generate a JSON manifest template, then validate the filled manifest with ``validate-production-overlay.ps1 -RequireImageEvidence -ImageEvidenceManifest <path>``.",
    "",
    "| Image | Digest pinned | SBOM attached | Signature verified | Provenance verified |",
    "| --- | --- | --- | --- | --- |"
)

foreach ($image in $legentImages) {
    $digestPinned = if ($image -match "@sha256:[a-f0-9]{64}$") { "yes" } else { "no - attach registry digest evidence" }
    $lines += "| ``$image`` | $digestPinned | required | required | required |"
}

$resolvedOutputPath = Resolve-OutputPath $OutputPath
Write-TextFile -Path $resolvedOutputPath -Content ($lines -join "`n")
Write-Host "Wrote image supply-chain checklist to $resolvedOutputPath"

if (-not [string]::IsNullOrWhiteSpace($ManifestOutputPath)) {
    $manifestImages = @()
    foreach ($image in $legentImages) {
        $manifestImages += [ordered] @{
            image = $image
            digest = Get-ImageDigest $image
            sbom = [ordered] @{
                uri = ""
                digest = ""
            }
            signature = [ordered] @{
                verified = $false
                transcript = ""
                verifier = ""
            }
            provenance = [ordered] @{
                verified = $false
                transcript = ""
                builderId = ""
                predicateType = ""
            }
        }
    }

    $manifest = [ordered] @{
        schemaVersion = "legent.image-evidence.v1"
        generatedAt = $generatedAt
        overlayPath = $OverlayPath
        notes = "Template only. Populate with registry-backed digests, SBOM references, signature verification, and provenance verification before strict promotion validation."
        images = $manifestImages
    }

    $resolvedManifestOutputPath = Resolve-OutputPath $ManifestOutputPath
    Write-TextFile -Path $resolvedManifestOutputPath -Content ($manifest | ConvertTo-Json -Depth 8)
    Write-Host "Wrote image evidence manifest template to $resolvedManifestOutputPath"
}
