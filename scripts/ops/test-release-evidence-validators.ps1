param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

$requiredScripts = @(
    "scripts/ops/validate-route-map.ps1",
    "scripts/ops/validate-production-overlay.ps1",
    "scripts/ops/validate-repo-artifact-hygiene.ps1",
    "scripts/ops/write-image-supply-chain-checklist.ps1",
    "scripts/ops/validate-image-evidence.ps1",
    "scripts/ops/validate-ga-evidence.ps1",
    "scripts/ops/validate-production-egress-evidence.ps1",
    "scripts/ops/validate-codex-state.ps1",
    "scripts/ops/release-gate.ps1"
)

foreach ($script in $requiredScripts) {
    if (-not (Test-Path $script)) { Fail "Missing required ops script: $script" }
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $script), [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count -gt 0) {
        Fail "PowerShell parser errors in $script"
    }
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("legent-evidence-test-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
try {
    $checklist = Join-Path $tempRoot "checklist.md"
    $manifest = Join-Path $tempRoot "manifest.json"
    & scripts/ops/write-image-supply-chain-checklist.ps1 -OutputPath $checklist -ManifestOutputPath $manifest
    if (-not (Test-Path $checklist)) { Fail "Checklist was not created." }
    if (-not (Test-Path $manifest)) { Fail "Manifest was not created." }
    Get-Content -Path $manifest -Raw | ConvertFrom-Json | Out-Null
    $validImageManifest = Join-Path $tempRoot "valid-image-manifest.json"
    $validEvidence = Join-Path $tempRoot "signature.txt"
    $validKustomization = Join-Path $tempRoot "kustomization.yml"
    Set-Content -Path $validEvidence -Value "verified" -Encoding UTF8
    $digest = "sha256:" + ("a" * 64)
    @"
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
images:
  - name: legent/frontend
    digest: $digest
"@ | Set-Content -Path $validKustomization -Encoding UTF8
    @{
        schemaVersion = 1
        images = @(@{
            image = "legent/frontend"
            digest = $digest
            sbom = "registry.example/legent/frontend.sbom"
            sbomDigest = "sha256:" + ("b" * 64)
            signatureEvidence = "signature.txt"
            provenanceEvidence = "signature.txt"
            builderId = "github-actions"
            predicateType = "https://slsa.dev/provenance/v1"
            reviewedBy = "release-reviewer"
            reviewedAt = "2026-05-20"
        })
    } | ConvertTo-Json -Depth 6 | Set-Content -Path $validImageManifest -Encoding UTF8
    & scripts/ops/validate-image-evidence.ps1 -ManifestPath $validImageManifest -EvidenceRoot $tempRoot -RequireDigests -KustomizationPath $validKustomization
    if (-not $?) { Fail "Valid image evidence should pass validation." }

    $badEgress = Join-Path $tempRoot "bad-egress.json"
    @{
        schemaVersion = 1
        reviewedBy = "release-reviewer"
        reviewedAt = "2026-05-20"
        egressRules = @(@{
            name = "bad"
            purpose = "bad broad egress"
            provider = "bad"
            cidrs = @("0.0.0.0/0")
            fqdns = @()
            ports = @(@{ protocol = "TCP"; port = 443 })
        })
    } | ConvertTo-Json -Depth 6 | Set-Content -Path $badEgress -Encoding UTF8
    $badOut = Join-Path $tempRoot "bad-egress.out"
    $badErr = Join-Path $tempRoot "bad-egress.err"
    $proc = Start-Process -FilePath "powershell" -ArgumentList @(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/ops/validate-production-egress-evidence.ps1",
        "-EvidencePath", $badEgress
    ) -Wait -PassThru -NoNewWindow -RedirectStandardOutput $badOut -RedirectStandardError $badErr
    if ($proc.ExitCode -eq 0) { Fail "Broad egress evidence should fail validation." }
    $global:LASTEXITCODE = 0
} finally {
    if (Test-Path $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

Write-Host "Release evidence validator self-test passed."
