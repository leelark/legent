param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([string[]] $Arguments)

    $output = & git @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed: $($output -join '; ')"
    }
    return @($output)
}

function Test-RuntimeLogPath {
    param([string] $Path)

    $normalized = $Path -replace "\\", "/"
    $leaf = [System.IO.Path]::GetFileName($normalized)

    if ($leaf -match "(?i)\.log(\.[0-9]+)?$") {
        return $true
    }

    if ($leaf -match "(?i)(^|[-_])(runtime|spring|application|server|access|error|debug|trace)[-_]?log(\.txt)?$") {
        return $true
    }

    if ($normalized -match "(?i)(^|/)(logs?|runtime-logs?)/") {
        return $true
    }

    return $false
}

Push-Location $RepoRoot
try {
    $trackedFiles = @(Invoke-Git @("ls-files"))
    $findings = [System.Collections.Generic.List[string]]::new()

    foreach ($file in $trackedFiles) {
        if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $file) -PathType Leaf)) {
            continue
        }
        if (Test-RuntimeLogPath $file) {
            $findings.Add("Tracked runtime log artifact: $file")
        }
    }

    $generatedPasswordPattern = ("Using generated security " + "password:" + "[[:space:]]*[^[:space:]]+")
    $searchOutput = @(git grep -n -I -E $generatedPasswordPattern -- 2>$null)
    $grepExit = $LASTEXITCODE
    if ($grepExit -eq 0) {
        foreach ($line in $searchOutput) {
            $path = ($line -split ":", 3)[0]
            if (-not [string]::IsNullOrWhiteSpace($path)) {
                $findings.Add("Tracked generated Spring security password output: $path")
            }
        }
    } elseif ($grepExit -ne 1) {
        throw "git grep failed while checking generated Spring security password output"
    }

    if ($findings.Count -gt 0) {
        $uniqueFindings = @($findings | Sort-Object -Unique)
        throw "Repository artifact hygiene validation failed:`n - $($uniqueFindings -join "`n - ")"
    }

    Write-Host "Repository artifact hygiene validation passed"
} finally {
    Pop-Location
}
