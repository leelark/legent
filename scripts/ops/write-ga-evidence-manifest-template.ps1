param(
    [string] $OutputPath = "docs/operations/ga-evidence-manifest.template.json",
    [string] $ReleaseVersion = "<release-version>",
    [string] $Environment = "production"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Resolve-OutputPath {
    param([string] $Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function New-EvidenceRecord {
    param(
        [string] $Type,
        [string] $File,
        [string[]] $MustContain
    )

    return [ordered]@{
        type = $Type
        status = "pass"
        date = "<yyyy-mm-ddThh:mm:ss+offset>"
        owner = "<evidence-owner>"
        files = @($File)
        mustContain = @($MustContain)
    }
}

$generatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$manifest = [ordered]@{
    schemaVersion = "legent.ga-evidence.v1"
    releaseVersion = $ReleaseVersion
    environment = $Environment
    generatedAt = $generatedAt
    notes = "Template only. Replace placeholders with current target-environment evidence and keep secrets, tokens, credentials, private keys, and customer data out of files."
    evidence = [ordered]@{
        "synthetic-smoke" = New-EvidenceRecord "synthetic-smoke" "synthetic-smoke-<date>.txt" @("synthetic smoke target base URL and pass result")
        "live-load" = New-EvidenceRecord "live-load" "live-load-require-live-<date>.json" @("RequireLive", "PASS")
        "restore-drill" = New-EvidenceRecord "restore-drill" "restore-drill-<date>.txt" @("restore", "post-restore smoke")
        "ci-gitleaks" = New-EvidenceRecord "ci-gitleaks" "ci-gitleaks-<date>.txt" @("gitleaks", "pass")
        "ci-trivy" = New-EvidenceRecord "ci-trivy" "ci-trivy-<date>.txt" @("trivy", "pass")
        "ci-sbom" = New-EvidenceRecord "ci-sbom" "filesystem-sbom-<date>.txt" @("sbom")
        "monitoring-alert-routing" = New-EvidenceRecord "monitoring-alert-routing" "monitoring-alert-routing-<date>.txt" @("platform-pager", "platform-primary")
        "tls-cert-ownership" = New-EvidenceRecord "tls-cert-ownership" "tls-cert-ownership-<date>.txt" @("legent-public-tls")
        "restricted-admission" = New-EvidenceRecord "restricted-admission" "restricted-admission-<date>.txt" @("restricted")
        "registry-digest" = New-EvidenceRecord "registry-digest" "image-evidence-validation-<date>.txt" @("sha256:<64-hex-image-digest>", "Image evidence manifest validation passed")
        "registry-sbom" = New-EvidenceRecord "registry-sbom" "image-evidence-validation-<date>.txt" @("SBOM", "sha256:<64-hex-sbom-digest>")
        "registry-signature" = New-EvidenceRecord "registry-signature" "image-evidence-validation-<date>.txt" @("signature", "verified")
        "registry-provenance" = New-EvidenceRecord "registry-provenance" "image-evidence-validation-<date>.txt" @("provenance", "attestation")
    }
}

$resolvedOutputPath = Resolve-OutputPath $OutputPath
$outputDirectory = Split-Path -Parent $resolvedOutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

Set-Content -LiteralPath $resolvedOutputPath -Value ($manifest | ConvertTo-Json -Depth 8) -Encoding UTF8
Write-Host "Wrote GA evidence manifest template to $resolvedOutputPath"
