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

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$frontendRoot = Join-Path $repoRoot "frontend"
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
$gaEvidenceRequired = $RequireGaEvidence -or (Test-EnvFlag $env:LEGENT_REQUIRE_GA_EVIDENCE)

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
    }
}

if ($RequireExternalEgressEvidence -or (Test-EnvFlag $env:LEGENT_REQUIRE_EXTERNAL_EGRESS_EVIDENCE)) {
    Invoke-GateStep "Production external egress evidence" {
        $scriptPath = Join-Path $repoRoot "scripts\ops\validate-production-egress-evidence.ps1"
        $arguments = @{}
        if (-not [string]::IsNullOrWhiteSpace($ExternalEgressEvidencePath)) {
            $arguments["SpecPath"] = $ExternalEgressEvidencePath
        }
        & $scriptPath @arguments
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
