param(
    [switch] $SkipBackend,
    [switch] $SkipFrontend,
    [switch] $SkipE2E,
    [switch] $SkipComposeSmoke
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

if (-not $SkipBackend) {
    Invoke-GateStep "Backend Maven tests" {
        Push-Location $repoRoot
        try {
            mvn test
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

Write-Host "Release gate passed"
