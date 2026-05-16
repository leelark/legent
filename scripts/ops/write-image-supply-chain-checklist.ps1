param(
    [string] $OverlayPath = "infrastructure/kubernetes/overlays/production",
    [string] $OutputPath = "legent-image-supply-chain-checklist.md"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$resolvedOutputPath = if ([System.IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath
} else {
    Join-Path $repoRoot $OutputPath
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
    "",
    "| Image | Digest pinned | SBOM attached | Signature verified | Provenance verified |",
    "| --- | --- | --- | --- | --- |"
)

foreach ($image in $legentImages) {
    $digestPinned = if ($image -match "@sha256:[a-f0-9]{64}$") { "yes" } else { "no - attach registry digest evidence" }
    $lines += "| ``$image`` | $digestPinned | required | required | required |"
}

$outputDirectory = Split-Path -Parent $resolvedOutputPath
if ($outputDirectory -and -not (Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

Set-Content -LiteralPath $resolvedOutputPath -Value ($lines -join "`n") -Encoding UTF8
Write-Host "Wrote image supply-chain checklist to $resolvedOutputPath"
