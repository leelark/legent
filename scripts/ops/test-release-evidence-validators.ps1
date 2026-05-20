param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Write-JsonFile($Path, $Value) {
    $Value | ConvertTo-Json -Depth 8 | Set-Content -Path $Path -Encoding UTF8
}

function Invoke-EgressValidator([string]$EvidencePath, [string]$Name) {
    $outPath = Join-Path $tempRoot "$Name.out"
    $errPath = Join-Path $tempRoot "$Name.err"
    return Start-Process -FilePath "powershell" -ArgumentList @(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/ops/validate-production-egress-evidence.ps1",
        "-EvidencePath", $EvidencePath
    ) -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput $outPath -RedirectStandardError $errPath
}

function Invoke-EgressRenderValidator([string]$EvidencePath, [string]$Name, [string]$GeneratedPolicyPath = $null, [switch]$UseExistingGeneratedPolicy) {
    $outPath = Join-Path $tempRoot "$Name.out"
    $errPath = Join-Path $tempRoot "$Name.err"
    $arguments = @(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/ops/validate-production-egress-policy-render.ps1",
        "-EvidencePath", $EvidencePath
    )
    if ($GeneratedPolicyPath) {
        $arguments += @("-GeneratedPolicyPath", $GeneratedPolicyPath)
    }
    if ($UseExistingGeneratedPolicy) {
        $arguments += "-UseExistingGeneratedPolicy"
    }
    return Start-Process -FilePath "powershell" -ArgumentList $arguments -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput $outPath -RedirectStandardError $errPath
}

function Assert-EgressFails([string]$Name, $Evidence, [string]$FailureMessage) {
    $path = Join-Path $tempRoot "$Name.json"
    Write-JsonFile $path $Evidence
    $proc = Invoke-EgressValidator $path $Name
    if ($proc.ExitCode -eq 0) { Fail $FailureMessage }
    $global:LASTEXITCODE = 0
}

function Assert-EgressPasses([string]$Name, $Evidence, [string]$FailureMessage) {
    $path = Join-Path $tempRoot "$Name.json"
    Write-JsonFile $path $Evidence
    & scripts/ops/validate-production-egress-evidence.ps1 -EvidencePath $path
    if (-not $?) { Fail $FailureMessage }
}

function New-EgressEvidence([string]$ReviewedBy, [string]$ReviewedAt, [string]$Provider, [string[]]$Cidrs, [string[]]$Fqdns = @()) {
    return @{
        schemaVersion = 1
        reviewedBy = $ReviewedBy
        reviewedAt = $ReviewedAt
        egressRules = @(@{
            name = "managed-postgres"
            purpose = "Production PostgreSQL database connectivity"
            provider = $Provider
            cidrs = $Cidrs
            fqdns = $Fqdns
            ports = @(@{ protocol = "TCP"; port = 5432 })
        })
    }
}

$requiredScripts = @(
    "scripts/ops/validate-route-map.ps1",
    "scripts/ops/validate-production-overlay.ps1",
    "scripts/ops/validate-repo-artifact-hygiene.ps1",
    "scripts/ops/write-image-supply-chain-checklist.ps1",
    "scripts/ops/validate-image-evidence.ps1",
    "scripts/ops/validate-ga-evidence.ps1",
    "scripts/ops/validate-production-egress-evidence.ps1",
    "scripts/ops/write-production-egress-policy.ps1",
    "scripts/ops/validate-production-egress-policy-render.ps1",
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

    Assert-EgressPasses "reviewed-public-egress" `
        (New-EgressEvidence "release-reviewer" "2026-05-20" "managed-database-provider" @("8.8.8.8/32")) `
        "Valid production public-CIDR egress evidence should pass validation."

    Assert-EgressPasses "reviewed-private-endpoint-egress" `
        (New-EgressEvidence "release-reviewer" "2026-05-20" "aws-private-link-rds" @("10.24.16.0/28")) `
        "Reviewed private endpoint egress evidence should pass validation."

    $renderEvidencePath = Join-Path $tempRoot "reviewed-render-egress.json"
    Write-JsonFile $renderEvidencePath (New-EgressEvidence "release-reviewer" "2026-05-20" "managed-database-provider" @("8.8.8.8/32"))
    $generatedPolicyPath = Join-Path $tempRoot "reviewed-external-egress.generated.yml"
    & scripts/ops/write-production-egress-policy.ps1 -EvidencePath $renderEvidencePath -OutputPath $generatedPolicyPath
    if (-not $?) { Fail "Reviewed egress policy writer should generate a policy for valid evidence." }
    if (-not (Test-Path $generatedPolicyPath)) { Fail "Reviewed egress policy writer did not create a generated policy." }
    $generatedPolicy = Get-Content -Path $generatedPolicyPath -Raw
    foreach ($requiredFragment in @(
        "kind: NetworkPolicy",
        "name: reviewed-external-egress",
        "legent.com/egress-evidence-sha256",
        "policyTypes:",
        "cidr: 8.8.8.8/32",
        "port: 5432"
    )) {
        if ($generatedPolicy -notmatch [regex]::Escape($requiredFragment)) {
            Fail "Generated reviewed egress policy missing fragment: $requiredFragment"
        }
    }
    & scripts/ops/validate-production-egress-policy-render.ps1 -EvidencePath $renderEvidencePath -GeneratedPolicyPath $generatedPolicyPath -UseExistingGeneratedPolicy
    if (-not $?) { Fail "Valid reviewed egress policy should render through Kustomize." }
    & scripts/ops/validate-production-egress-policy-render.ps1 -EvidencePath $renderEvidencePath
    if (-not $?) { Fail "Strict render proof should generate and render reviewed egress policy from evidence." }

    $missingGenerated = Join-Path $tempRoot "missing-reviewed-external-egress.generated.yml"
    $missingGeneratedProc = Invoke-EgressRenderValidator $renderEvidencePath "missing-generated-egress-policy" $missingGenerated -UseExistingGeneratedPolicy
    if ($missingGeneratedProc.ExitCode -eq 0) { Fail "Missing generated reviewed egress policy should fail render validation." }
    $global:LASTEXITCODE = 0

    $staleEvidencePath = Join-Path $tempRoot "reviewed-render-egress-stale.json"
    Write-JsonFile $staleEvidencePath (New-EgressEvidence "release-reviewer" "2026-05-20" "managed-database-provider" @("1.1.1.1/32"))
    $staleGeneratedPolicyPath = Join-Path $tempRoot "stale-reviewed-external-egress.generated.yml"
    & scripts/ops/write-production-egress-policy.ps1 -EvidencePath $staleEvidencePath -OutputPath $staleGeneratedPolicyPath
    if (-not $?) { Fail "Stale fixture generation should succeed before mismatch validation." }
    $staleProc = Invoke-EgressRenderValidator $renderEvidencePath "stale-generated-egress-policy" $staleGeneratedPolicyPath -UseExistingGeneratedPolicy
    if ($staleProc.ExitCode -eq 0) { Fail "Stale generated reviewed egress policy should fail render validation." }
    $global:LASTEXITCODE = 0

    $templateProc = Invoke-EgressValidator "docs/operations/production-egress-evidence.template.json" "template-egress"
    if ($templateProc.ExitCode -eq 0) { Fail "Template egress evidence should fail validation." }
    $global:LASTEXITCODE = 0

    Assert-EgressFails "bad-broad-egress" @{
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
    } "Broad egress evidence should fail validation."

    Assert-EgressFails "bad-schema-version" @{
        schemaVersion = 2
        reviewedBy = "release-reviewer"
        reviewedAt = "2026-05-20"
        egressRules = @(@{
            name = "managed-postgres"
            purpose = "Production PostgreSQL database connectivity"
            provider = "managed-database-provider"
            cidrs = @("8.8.8.8/32")
            fqdns = @()
            ports = @(@{ protocol = "TCP"; port = 5432 })
        })
    } "Unsupported egress evidence schemaVersion should fail validation."

    foreach ($documentationCidr in @("192.0.2.10/32", "198.51.100.10/32", "203.0.113.10/32", "2001:db8::1/128")) {
        $caseName = "bad-documentation-cidr-" + ($documentationCidr -replace "[^A-Za-z0-9-]", "-")
        Assert-EgressFails $caseName `
            (New-EgressEvidence "release-reviewer" "2026-05-20" "managed-database-provider" @($documentationCidr)) `
            "Documentation/example CIDR evidence should fail validation: $documentationCidr"
    }

    foreach ($badCidr in @("127.0.0.1/32", "169.254.1.1/32", "224.0.0.1/32", "0.0.0.0/1", "128.0.0.0/1", "8.0.0.0/8", "8.0.0.0/9", "10.0.0.0/8", "172.16.0.0/12", "127.1/32", "2130706433/32", "010.000.000.001/32", "192.168.1/32", "8.8.8.8/24")) {
        $caseName = "bad-cidr-" + ($badCidr -replace "[^A-Za-z0-9-]", "-")
        Assert-EgressFails $caseName `
            (New-EgressEvidence "release-reviewer" "2026-05-20" "managed-database-provider" @($badCidr)) `
            "Reserved, broad, or non-canonical CIDR evidence should fail validation: $badCidr"
    }

    Assert-EgressFails "bad-placeholder-reviewer" `
        (New-EgressEvidence "example-release-reviewer" "2026-05-20" "managed-database-provider" @("8.8.8.8/32")) `
        "Placeholder reviewer evidence should fail validation."

    Assert-EgressFails "bad-placeholder-provider" `
        (New-EgressEvidence "release-reviewer" "2026-05-20" "example-managed-database-provider" @("8.8.8.8/32")) `
        "Placeholder provider evidence should fail validation."

    Assert-EgressFails "bad-placeholder-date" `
        (New-EgressEvidence "release-reviewer" "TBD" "managed-database-provider" @("8.8.8.8/32")) `
        "Placeholder reviewedAt evidence should fail validation."

    Assert-EgressFails "bad-future-date" `
        (New-EgressEvidence "release-reviewer" "2999-01-01" "managed-database-provider" @("8.8.8.8/32")) `
        "Future reviewedAt evidence should fail validation."

    Assert-EgressFails "bad-fqdn-entry" `
        (New-EgressEvidence "release-reviewer" "2026-05-20" "managed-database-provider" @("8.8.8.8/32") @("api.example.com")) `
        "FQDN evidence should fail until a supported CNI FQDN policy generator exists."
} finally {
    if (Test-Path $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

Write-Host "Release evidence validator self-test passed."
