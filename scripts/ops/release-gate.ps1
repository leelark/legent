param(
    [switch] $SkipBackend,
    [switch] $SkipFrontend,
    [switch] $SkipE2E,
    [switch] $SkipEnvValidation,
    [switch] $SkipRouteValidation,
    [switch] $SkipComposeSmoke,
    [switch] $SkipVisualE2E,
    [switch] $SkipKustomize,
    [switch] $RunSyntheticSmoke,
    [string] $SmokeBaseUrl = $env:LEGENT_SMOKE_BASE_URL,
    [switch] $RequireImageDigests,
    [switch] $RequireImageEvidence,
    [string] $ImageEvidenceManifest = $env:LEGENT_IMAGE_EVIDENCE_MANIFEST,
    [string] $EvidenceDir = $env:LEGENT_GA_EVIDENCE_DIR,
    [switch] $RequireGaEvidence,
    [int] $GaEvidenceMaxAgeDays = 14,
    [switch] $RequireExternalEgressEvidence,
    [string] $ExternalEgressEvidencePath = $env:LEGENT_EXTERNAL_EGRESS_EVIDENCE_PATH
)

$ErrorActionPreference = "Stop"

function Test-EnvFlag {
    param([string] $Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    $normalized = $Value.Trim().ToLowerInvariant()
    return @("1", "true", "yes", "y", "on", "required") -contains $normalized
}

function Invoke-GateStep($Name, [scriptblock] $Command) {
    $started = Get-Date
    Write-Host "==> $Name"
    & $Command
    $elapsed = (Get-Date) - $started
    Write-Host "<== $Name completed in $([math]::Round($elapsed.TotalSeconds, 1))s"
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,
        [string[]] $Arguments = @(),
        [switch] $SuppressOutput
    )

    $global:LASTEXITCODE = 0
    if ($SuppressOutput) {
        & $FilePath @Arguments | Out-Null
    } else {
        & $FilePath @Arguments
    }
    $exitCode = $global:LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Native command failed with exit code $exitCode`: $FilePath $($Arguments -join ' ')"
    }
}

function Get-JsonProperty {
    param(
        [object] $Object,
        [string] $Name
    )

    if ($null -eq $Object) {
        return $null
    }

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }

    return $property.Value
}

function Get-FirstJsonProperty {
    param(
        [object] $Object,
        [string[]] $Names
    )

    foreach ($name in $Names) {
        $value = Get-JsonProperty $Object $name
        if ($null -ne $value) {
            return $value
        }
    }

    return $null
}

function Test-ExternalEgressPlaceholder {
    param([object] $Value)

    if ($null -eq $Value) {
        return $true
    }

    $text = "$Value".Trim()
    if ($text.Length -eq 0) {
        return $true
    }

    return (
        $text -match "[<>]" -or
        $text -match "\.\.\." -or
        $text -match "(?i)^(todo|tbd|n/a|na|none|null|unknown|changeme|change_me|replace_me|placeholder|sample|example|dummy)$" -or
        $text -match "(?i)\b(todo|tbd|fixme|changeme|change_me|replace_me|placeholder)\b" -or
        $text -match "(?i)example\.(com|net|org)\b"
    )
}

function Test-SecretLikePath {
    param([string] $Path)

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
        if ($segment.ToLowerInvariant() -in @(".env", ".ssh", "secrets", "credentials")) {
            return $true
        }
    }

    return $false
}

function Test-UriReference {
    param([string] $Reference)

    return $Reference -match "^[a-z][a-z0-9+.-]*://"
}

function Resolve-ExternalEgressArtifactReference {
    param(
        [string] $Reference,
        [string] $SpecDirectory
    )

    if ([System.IO.Path]::IsPathRooted($Reference)) {
        return [System.IO.Path]::GetFullPath($Reference)
    }

    $repoCandidate = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Reference))
    if (Test-Path -LiteralPath $repoCandidate -PathType Leaf) {
        return $repoCandidate
    }

    return [System.IO.Path]::GetFullPath((Join-Path $SpecDirectory $Reference))
}

function Add-EvidenceReferenceValue {
    param(
        [object] $Value,
        [System.Collections.Generic.List[string]] $References
    )

    if ($null -eq $Value) {
        return
    }

    if ($Value -is [string]) {
        if (-not [string]::IsNullOrWhiteSpace($Value)) {
            $References.Add($Value.Trim())
        }
        return
    }

    if ($Value -is [System.Collections.IEnumerable]) {
        foreach ($item in $Value) {
            Add-EvidenceReferenceValue $item $References
        }
        return
    }

    foreach ($name in @("path", "file", "files", "uri", "url", "artifact", "artifacts", "evidenceFile", "evidenceFiles", "transcript", "transcripts")) {
        $nestedValue = Get-JsonProperty $Value $name
        if ($null -ne $nestedValue) {
            Add-EvidenceReferenceValue $nestedValue $References
        }
    }
}

function Get-EvidenceReferences {
    param([object] $Value)

    $references = [System.Collections.Generic.List[string]]::new()
    Add-EvidenceReferenceValue $Value $references
    return @($references | Select-Object -Unique)
}

function Assert-ExternalEgressReference {
    param(
        [string] $Path,
        [string] $Reference,
        [string] $SpecDirectory
    )

    if (Test-ExternalEgressPlaceholder $Reference) {
        throw "$Path must reference a real rendered/applied evidence artifact, not a placeholder"
    }

    if (Test-SecretLikePath $Reference) {
        throw "$Path references a secret/env-like path: $Reference"
    }

    if (Test-UriReference $Reference) {
        return
    }

    $fullPath = Resolve-ExternalEgressArtifactReference $Reference $SpecDirectory
    if (Test-SecretLikePath $fullPath) {
        throw "$Path resolves to a secret/env-like path: $fullPath"
    }

    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "$Path references a missing local evidence artifact: $Reference"
    }

    $fileText = Get-Content -LiteralPath $fullPath -Raw -Encoding UTF8
    if ($fileText -match "(?i)\b(TBD|TODO|FIXME|CHANGEME|CHANGE_ME|REPLACE_ME|placeholder)\b|<[^>\r\n]*(todo|tbd|placeholder|path|artifact|ticket)[^>\r\n]*>") {
        throw "$Path evidence artifact contains placeholder text: $Reference"
    }
}

function Assert-ExternalEgressEvidenceField {
    param(
        [string] $Path,
        [object] $Value,
        [string] $SpecDirectory
    )

    $references = @(Get-EvidenceReferences $Value)
    if ($references.Count -eq 0) {
        throw "$Path must reference at least one local evidence artifact or immutable artifact URI"
    }

    for ($referenceIndex = 0; $referenceIndex -lt $references.Count; $referenceIndex++) {
        Assert-ExternalEgressReference "$Path[$referenceIndex]" $references[$referenceIndex] $SpecDirectory
    }
}

function Assert-ExternalEgressPolicyEvidence {
    param([string] $SpecPath)

    $resolvedSpecPath = if ([System.IO.Path]::IsPathRooted($SpecPath)) {
        [System.IO.Path]::GetFullPath($SpecPath)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $repoRoot $SpecPath))
    }

    $specDirectory = Split-Path -Parent $resolvedSpecPath
    $spec = Get-Content -LiteralPath $resolvedSpecPath -Raw | ConvertFrom-Json
    $policyEvidence = Get-JsonProperty $spec "policyEvidence"
    if ($null -eq $policyEvidence) {
        throw "policyEvidence is required when external egress evidence is supplied to release-gate.ps1"
    }

    Assert-ExternalEgressEvidenceField "policyEvidence.renderedArtifacts" (Get-FirstJsonProperty $policyEvidence @("renderedArtifacts", "renderedArtifact", "renderedPolicies", "renderedPolicy")) $specDirectory
    Assert-ExternalEgressEvidenceField "policyEvidence.appliedEvidence" (Get-FirstJsonProperty $policyEvidence @("appliedEvidence", "applyEvidence", "applicationEvidence", "admissionEvidence", "applyTranscript")) $specDirectory
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$frontendRoot = Join-Path $repoRoot "frontend"
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
$gaEvidenceRequired = $RequireGaEvidence -or (Test-EnvFlag $env:LEGENT_REQUIRE_GA_EVIDENCE)
$externalEgressEvidenceRequired = $RequireExternalEgressEvidence -or (Test-EnvFlag $env:LEGENT_REQUIRE_EXTERNAL_EGRESS_EVIDENCE)
$externalEgressEvidenceProvided = -not [string]::IsNullOrWhiteSpace($ExternalEgressEvidencePath)

if ($gaEvidenceRequired -and [string]::IsNullOrWhiteSpace($EvidenceDir)) {
    throw "EvidenceDir is required when GA evidence validation is required"
}

if (-not $SkipEnvValidation) {
    Invoke-GateStep "Environment preflight" {
        $scriptPath = Join-Path $repoRoot "scripts\ops\validate-env.ps1"
        & $scriptPath
    }
}

if (-not $SkipRouteValidation) {
    Invoke-GateStep "Gateway route contract" {
        $scriptPath = Join-Path $repoRoot "scripts\ops\validate-route-map.ps1"
        & $scriptPath
    }
}

if ($gaEvidenceRequired -or -not [string]::IsNullOrWhiteSpace($EvidenceDir)) {
    Invoke-GateStep "GA evidence pack validation" {
        $scriptPath = Join-Path $repoRoot "scripts\ops\validate-ga-evidence.ps1"
        & $scriptPath -EvidenceDir $EvidenceDir -MaxAgeDays $GaEvidenceMaxAgeDays
    }
}

if (-not $SkipBackend) {
    Invoke-GateStep "Backend Maven clean package" {
        Push-Location $repoRoot
        try {
            Invoke-NativeCommand $mavenWrapper @("clean", "package", "-T", "1C")
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipFrontend) {
    Invoke-GateStep "Frontend lint" {
        Push-Location $frontendRoot
        try {
            Invoke-NativeCommand "npm" @("run", "lint")
        } finally {
            Pop-Location
        }
    }

    Invoke-GateStep "Frontend sanitizer regression" {
        Push-Location $frontendRoot
        $previousSkipWebServer = $env:PLAYWRIGHT_SKIP_WEB_SERVER
        try {
            $env:PLAYWRIGHT_SKIP_WEB_SERVER = "1"
            Invoke-NativeCommand "npm" @("run", "test:e2e:sanitize")
        } finally {
            if ($null -eq $previousSkipWebServer) {
                Remove-Item Env:\PLAYWRIGHT_SKIP_WEB_SERVER -ErrorAction SilentlyContinue
            } else {
                $env:PLAYWRIGHT_SKIP_WEB_SERVER = $previousSkipWebServer
            }
            Pop-Location
        }
    }

    Invoke-GateStep "Frontend production build" {
        Push-Location $frontendRoot
        try {
            Invoke-NativeCommand "npm" @("run", "build:ci")
        } finally {
            Pop-Location
        }
    }

    if (-not $SkipE2E) {
        Invoke-GateStep "Frontend Playwright smoke" {
            Push-Location $frontendRoot
            try {
                Invoke-NativeCommand "npm" @("run", "test:e2e:smoke")
            } finally {
                Pop-Location
            }
        }

        if (-not $SkipVisualE2E) {
            Invoke-GateStep "Frontend visual smoke" {
                Push-Location $frontendRoot
                try {
                    Invoke-NativeCommand "npm" @("run", "test:e2e:visual")
                } finally {
                    Pop-Location
                }
            }
        }
    }
}

if (-not $SkipComposeSmoke) {
    Invoke-GateStep "Docker Compose config smoke" {
        Push-Location $repoRoot
        try {
            Invoke-NativeCommand "docker" @("compose", "config", "--quiet")
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipKustomize) {
    Invoke-GateStep "Kubernetes overlay render" {
        Push-Location $repoRoot
        try {
            $overlays = @(
                "infrastructure/kubernetes/base",
                "infrastructure/kubernetes/overlays/production",
                "infrastructure/kubernetes/overlays/global/global-active-active",
                "infrastructure/kubernetes/overlays/global/global-primary",
                "infrastructure/kubernetes/overlays/global/global-standby",
                "infrastructure/kubernetes/observability"
            )
            foreach ($overlay in $overlays) {
                Invoke-NativeCommand "kubectl" @("kustomize", $overlay) -SuppressOutput
            }
        } finally {
            Pop-Location
        }
    }

    Invoke-GateStep "Production overlay drift checks" {
        $scriptPath = Join-Path $repoRoot "scripts\ops\validate-production-overlay.ps1"
        $arguments = @{}
        if ($RequireImageDigests -or (Test-EnvFlag $env:LEGENT_REQUIRE_IMAGE_DIGESTS)) {
            $arguments["RequireImageDigests"] = $true
        }
        if ($RequireImageEvidence -or (Test-EnvFlag $env:LEGENT_REQUIRE_IMAGE_EVIDENCE)) {
            $arguments["RequireImageEvidence"] = $true
        }
        if (-not [string]::IsNullOrWhiteSpace($ImageEvidenceManifest)) {
            $arguments["ImageEvidenceManifest"] = $ImageEvidenceManifest
        }
        & $scriptPath @arguments

        if ($RequireImageEvidence -or (Test-EnvFlag $env:LEGENT_REQUIRE_IMAGE_EVIDENCE) -or -not [string]::IsNullOrWhiteSpace($ImageEvidenceManifest)) {
            $imageEvidenceScriptPath = Join-Path $repoRoot "scripts\ops\validate-image-evidence.ps1"
            & $imageEvidenceScriptPath -ManifestPath $ImageEvidenceManifest
        }
    }
}

if ($externalEgressEvidenceRequired -or $externalEgressEvidenceProvided) {
    Invoke-GateStep "Production external egress evidence" {
        $scriptPath = Join-Path $repoRoot "scripts\ops\validate-production-egress-evidence.ps1"
        $egressSpecPath = if (-not [string]::IsNullOrWhiteSpace($ExternalEgressEvidencePath)) { $ExternalEgressEvidencePath } else { "docs\operations\production-egress-evidence.json" }
        $arguments = @{ SpecPath = $egressSpecPath }
        & $scriptPath @arguments
        Assert-ExternalEgressPolicyEvidence $egressSpecPath
    }
}

if ($RunSyntheticSmoke) {
    Invoke-GateStep "Synthetic API smoke" {
        $scriptPath = Join-Path $repoRoot "scripts\ops\synthetic-smoke.ps1"
        $arguments = @{}
        if ($SmokeBaseUrl) {
            $arguments["BaseUrl"] = $SmokeBaseUrl
        }
        & $scriptPath @arguments
    }
}

Write-Host "Release gate passed"
