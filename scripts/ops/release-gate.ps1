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
    [string] $SmokeBaseUrl = $env:LEGENT_SMOKE_BASE_URL
)

$ErrorActionPreference = "Stop"

function Invoke-GateStep($Name, [scriptblock] $Command) {
    $started = Get-Date
    Write-Host "==> $Name"
    & $Command
    $elapsed = (Get-Date) - $started
    Write-Host "<== $Name completed in $([math]::Round($elapsed.TotalSeconds, 1))s"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$frontendRoot = Join-Path $repoRoot "frontend"
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"

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

if (-not $SkipBackend) {
    Invoke-GateStep "Backend Maven clean package" {
        Push-Location $repoRoot
        try {
            & $mavenWrapper clean package -T 1C
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipFrontend) {
    Invoke-GateStep "Frontend lint" {
        Push-Location $frontendRoot
        try {
            npm run lint
        } finally {
            Pop-Location
        }
    }

    Invoke-GateStep "Frontend production build" {
        Push-Location $frontendRoot
        try {
            npm run build:ci
        } finally {
            Pop-Location
        }
    }

    if (-not $SkipE2E) {
        Invoke-GateStep "Frontend Playwright smoke" {
            Push-Location $frontendRoot
            try {
                npm run test:e2e:smoke
            } finally {
                Pop-Location
            }
        }

        if (-not $SkipVisualE2E) {
            Invoke-GateStep "Frontend visual smoke" {
                Push-Location $frontendRoot
                try {
                    npm run test:e2e:visual
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
            docker compose config --quiet
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
                kubectl kustomize $overlay | Out-Null
            }
        } finally {
            Pop-Location
        }
    }

    Invoke-GateStep "Production overlay drift checks" {
        $scriptPath = Join-Path $repoRoot "scripts\ops\validate-production-overlay.ps1"
        & $scriptPath
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
