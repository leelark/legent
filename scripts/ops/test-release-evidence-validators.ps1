param(
    [ValidateRange(1, 3600)][int]$ChildProcessTimeoutSeconds = 60
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$script:ChildProcessTimeoutSeconds = $ChildProcessTimeoutSeconds

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Write-JsonFile($Path, $Value) {
    $Value | ConvertTo-Json -Depth 8 | Set-Content -Path $Path -Encoding UTF8
}

function Get-FileExcerpt([string]$Path) {
    if (-not (Test-Path $Path)) { return "" }
    $content = Get-Content -Path $Path -Raw -ErrorAction SilentlyContinue
    if (-not $content) { return "" }
    $trimmed = $content.Trim()
    if ($trimmed.Length -le 2000) { return $trimmed }
    return $trimmed.Substring(0, 2000) + "... [truncated]"
}

function Stop-ProcessTree([int]$ProcessId) {
    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId ([int]$child.ProcessId)
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Invoke-ValidatorProcess([string]$Name, [string[]]$Arguments) {
    $outPath = Join-Path $tempRoot "$Name.out"
    $errPath = Join-Path $tempRoot "$Name.err"
    $proc = Start-Process -FilePath "powershell" `
        -ArgumentList $Arguments `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardOutput $outPath `
        -RedirectStandardError $errPath

    $completed = $proc.WaitForExit($script:ChildProcessTimeoutSeconds * 1000)
    if (-not $completed) {
        Stop-ProcessTree -ProcessId $proc.Id
        $proc.WaitForExit(5000) | Out-Null
        $stdout = Get-FileExcerpt $outPath
        $stderr = Get-FileExcerpt $errPath
        Fail "Validator fixture '$Name' exceeded ${script:ChildProcessTimeoutSeconds}s. stdout=[$stdout] stderr=[$stderr]"
    }
    $proc.Refresh()
    $proc | Add-Member -NotePropertyName StdoutPath -NotePropertyValue $outPath -Force
    $proc | Add-Member -NotePropertyName StderrPath -NotePropertyValue $errPath -Force
    return $proc
}

function Invoke-EgressValidator([string]$EvidencePath, [string]$Name) {
    return Invoke-ValidatorProcess $Name @(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/ops/validate-production-egress-evidence.ps1",
        "-EvidencePath", $EvidencePath
    )
}

function Invoke-EgressRenderValidator([string]$EvidencePath, [string]$Name, [string]$GeneratedPolicyPath = $null, [switch]$UseExistingGeneratedPolicy) {
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
    return Invoke-ValidatorProcess $Name $arguments
}

function Invoke-GaValidator([string]$EvidenceDir, [string]$Name, [string]$ManifestPath = $null) {
    $arguments = @(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/ops/validate-ga-evidence.ps1",
        "-EvidenceDir", $EvidenceDir
    )
    if ($ManifestPath) {
        $arguments += @("-ManifestPath", $ManifestPath)
    }
    return Invoke-ValidatorProcess $Name $arguments
}

function Invoke-ImageValidator([string]$ManifestPath, [string]$Name, [string]$EvidenceRoot, [string]$KustomizationPath) {
    return Invoke-ValidatorProcess $Name @(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/ops/validate-image-evidence.ps1",
        "-ManifestPath", $ManifestPath,
        "-EvidenceRoot", $EvidenceRoot,
        "-RequireDigests",
        "-KustomizationPath", $KustomizationPath
    )
}

function Invoke-ReleaseGate([string]$Name, [string[]]$Arguments) {
    return Invoke-ValidatorProcess $Name (@(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/ops/release-gate.ps1"
    ) + $Arguments)
}

function Assert-ReleaseGateFails([string]$Name, [string[]]$Arguments, [string]$ExpectedMessage, [string]$FailureMessage) {
    $proc = Invoke-ReleaseGate $Name $Arguments
    if ($proc.ExitCode -eq 0) { Fail $FailureMessage }
    $combinedOutput = (Get-FileExcerpt $proc.StdoutPath) + "`n" + (Get-FileExcerpt $proc.StderrPath)
    if ($combinedOutput -notmatch [regex]::Escape($ExpectedMessage)) {
        Fail "$FailureMessage Expected message was not found. stdout/stderr=[$combinedOutput]"
    }
    $global:LASTEXITCODE = 0
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

function Assert-GaFails([string]$Name, [string]$EvidenceDir, $Manifest, [string]$FailureMessage) {
    $path = Join-Path $EvidenceDir "ga-evidence-manifest.json"
    Write-JsonFile $path $Manifest
    $proc = Invoke-GaValidator $EvidenceDir $Name $path
    if ($proc.ExitCode -eq 0) { Fail $FailureMessage }
    $global:LASTEXITCODE = 0
}

function Assert-GaPasses([string]$Name, [string]$EvidenceDir, $Manifest, [string]$FailureMessage) {
    $path = Join-Path $EvidenceDir "ga-evidence-manifest.json"
    Write-JsonFile $path $Manifest
    & scripts/ops/validate-ga-evidence.ps1 -EvidenceDir $EvidenceDir -ManifestPath $path
    if (-not $?) { Fail $FailureMessage }
}

function Assert-ImageFails([string]$Name, $Manifest, [string]$EvidenceRoot, [string]$KustomizationPath, [string]$FailureMessage) {
    $path = Join-Path $tempRoot "$Name.json"
    Write-JsonFile $path $Manifest
    $proc = Invoke-ImageValidator $path $Name $EvidenceRoot $KustomizationPath
    if ($proc.ExitCode -eq 0) { Fail $FailureMessage }
    $global:LASTEXITCODE = 0
}

function Assert-ImagePasses([string]$Name, $Manifest, [string]$EvidenceRoot, [string]$KustomizationPath, [string]$FailureMessage) {
    $path = Join-Path $tempRoot "$Name.json"
    Write-JsonFile $path $Manifest
    & scripts/ops/validate-image-evidence.ps1 -ManifestPath $path -EvidenceRoot $EvidenceRoot -RequireDigests -KustomizationPath $KustomizationPath
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

function New-GaManifest([hashtable]$Overrides = @{}) {
    $manifest = [ordered]@{
        schemaVersion = 1
        syntheticSmoke = "synthetic-smoke.txt"
        liveLoad = "live-load.txt"
        restoreDrill = "restore-drill.txt"
        ciSecurityTranscript = "ci-security.txt"
        filesystemSbom = "filesystem-sbom.txt"
        monitoringHandoff = "monitoring-handoff.txt"
        tlsCertificate = "tls-certificate.txt"
        restrictedAdmission = "restricted-admission.txt"
        registryImageEvidence = "registry-image-evidence.json"
    }
    foreach ($key in $Overrides.Keys) {
        if ($null -eq $Overrides[$key]) {
            $manifest.Remove($key)
        } else {
            $manifest[$key] = $Overrides[$key]
        }
    }
    return $manifest
}

function New-ImageManifest([string]$Image, [string]$Digest, [string]$SignatureEvidence = "signature.txt", [string]$ProvenanceEvidence = "signature.txt", [object[]]$ExtraImages = @()) {
    $images = @(@{
        image = $Image
        digest = $Digest
        sbom = "registry.example/legent/frontend.sbom"
        sbomDigest = "sha256:" + ("b" * 64)
        signatureEvidence = $SignatureEvidence
        provenanceEvidence = $ProvenanceEvidence
        builderId = "github-actions"
        predicateType = "https://slsa.dev/provenance/v1"
        reviewedBy = "release-reviewer"
        reviewedAt = "2026-05-20"
    })
    $images += @($ExtraImages)
    return @{ schemaVersion = 1; images = $images }
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

$releaseGateSource = Get-Content -Path "scripts/ops/release-gate.ps1" -Raw
if ($releaseGateSource -notmatch 'docker\s+compose\s+--env-file\s+\$ComposeEnvFile\s+config\s+--quiet') {
    Fail "release-gate.ps1 must run docker compose config with the reviewed ComposeEnvFile instead of ambient environment discovery."
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

    Assert-ReleaseGateFails "strict-release-gate-skip-flags" `
        @(
            "-RequireExternalEgressEvidence",
            "-RequireGaEvidence",
            "-RequireImageEvidence",
            "-RequireImageDigests",
            "-SkipBackend",
            "-SkipFrontend",
            "-SkipCompose",
            "-SkipKustomize"
        ) `
        "Strict release promotion cannot use local gate skip flags" `
        "Strict release promotion should reject local gate skip flags before evidence validation."

    $missingImageSchemaManifest = New-ImageManifest "legent/frontend" $digest
    $missingImageSchemaManifest.Remove("schemaVersion")
    Assert-ImageFails "missing-image-schema-version" `
        $missingImageSchemaManifest `
        $tempRoot `
        $validKustomization `
        "Image evidence missing schemaVersion should fail validation."

    $unsupportedImageSchemaManifest = New-ImageManifest "legent/frontend" $digest
    $unsupportedImageSchemaManifest["schemaVersion"] = 2
    Assert-ImageFails "unsupported-image-schema-version" `
        $unsupportedImageSchemaManifest `
        $tempRoot `
        $validKustomization `
        "Unsupported image evidence schemaVersion should fail validation."

    $secondDigest = "sha256:" + ("c" * 64)
    $multiImageKustomization = Join-Path $tempRoot "multi-image-kustomization.yml"
    @"
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
images:
  - name: legent/frontend
    digest: $digest
  - name: legent/api
    digest: $secondDigest
"@ | Set-Content -Path $multiImageKustomization -Encoding UTF8
    Assert-ImageFails "missing-production-image" `
        (New-ImageManifest "legent/frontend" $digest) `
        $tempRoot `
        $multiImageKustomization `
        "Image evidence missing a production image should fail validation."

    $extraImage = @{
        image = "legent/worker"
        digest = $secondDigest
        sbom = "registry.example/legent/worker.sbom"
        sbomDigest = "sha256:" + ("d" * 64)
        signatureEvidence = "signature.txt"
        provenanceEvidence = "signature.txt"
        builderId = "github-actions"
        predicateType = "https://slsa.dev/provenance/v1"
        reviewedBy = "release-reviewer"
        reviewedAt = "2026-05-20"
    }
    Assert-ImageFails "extra-image-evidence" `
        (New-ImageManifest "legent/frontend" $digest "signature.txt" "signature.txt" @($extraImage)) `
        $tempRoot `
        $validKustomization `
        "Image evidence containing an image not in production kustomization should fail validation."

    Assert-ImageFails "digest-mismatch-image-evidence" `
        (New-ImageManifest "legent/frontend" $secondDigest) `
        $tempRoot `
        $validKustomization `
        "Image evidence digest mismatch should fail validation."

    Assert-ImageFails "absolute-image-evidence-path" `
        (New-ImageManifest "legent/frontend" $digest (Join-Path $tempRoot "signature.txt")) `
        $tempRoot `
        $validKustomization `
        "Absolute image evidence paths should fail validation."

    Assert-ImageFails "escaping-image-evidence-path" `
        (New-ImageManifest "legent/frontend" $digest "..\outside-signature.txt") `
        $tempRoot `
        $validKustomization `
        "Image evidence paths escaping the evidence root should fail validation."

    $gaRoot = Join-Path $tempRoot "ga-evidence"
    New-Item -ItemType Directory -Force -Path $gaRoot | Out-Null
    $validGaManifest = New-GaManifest
    foreach ($artifact in @($validGaManifest.GetEnumerator() | Where-Object { $_.Key -ne "schemaVersion" } | ForEach-Object { $_.Value })) {
        Set-Content -Path (Join-Path $gaRoot ([string]$artifact)) -Value "reviewed evidence" -Encoding UTF8
    }
    Assert-GaPasses "valid-ga-evidence" `
        $gaRoot `
        $validGaManifest `
        "Valid GA evidence fixture should pass validation."

    Assert-GaFails "missing-ga-schema-version" `
        $gaRoot `
        (New-GaManifest @{ schemaVersion = $null }) `
        "GA evidence missing schemaVersion should fail validation."

    Assert-GaFails "unsupported-ga-schema-version" `
        $gaRoot `
        (New-GaManifest @{ schemaVersion = 2 }) `
        "Unsupported GA evidence schemaVersion should fail validation."

    Assert-GaFails "placeholder-ga-evidence-field" `
        $gaRoot `
        (New-GaManifest @{ syntheticSmoke = "replace-with-smoke.txt" }) `
        "GA evidence placeholder values should fail validation."

    Assert-GaFails "missing-ga-evidence-artifact" `
        $gaRoot `
        (New-GaManifest @{ liveLoad = "missing-load.txt" }) `
        "GA evidence missing artifacts should fail validation."

    Assert-GaFails "absolute-ga-evidence-path" `
        $gaRoot `
        (New-GaManifest @{ syntheticSmoke = (Join-Path $gaRoot "synthetic-smoke.txt") }) `
        "GA evidence absolute paths should fail validation."

    Assert-GaFails "escaping-ga-evidence-path" `
        $gaRoot `
        (New-GaManifest @{ syntheticSmoke = "..\synthetic-smoke.txt" }) `
        "GA evidence paths escaping the evidence root should fail validation."

    Assert-GaFails "missing-ga-required-field" `
        $gaRoot `
        (New-GaManifest @{ tlsCertificate = $null }) `
        "GA evidence missing required fields should fail validation."

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
