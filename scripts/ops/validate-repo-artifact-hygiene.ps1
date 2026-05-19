param(
    [string]$RepoRoot = "."
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

$tracked = git -C $RepoRoot ls-files
if ($LASTEXITCODE -ne 0) { Fail "git ls-files failed." }

$errors = New-Object System.Collections.Generic.List[string]

foreach ($path in $tracked) {
    $normalized = $path -replace "\\", "/"
    if ($normalized -match "(^|/)\.env($|\.|/)") {
        if ($normalized -ne ".env.example") {
            $errors.Add("Tracked environment file is not allowed: $path")
        }
    }
    if ($normalized -match "(^|/)(node_modules|target|\.next|dist|build|coverage)(/|$)") {
        $errors.Add("Tracked generated dependency/build artifact: $path")
    }
    if ($normalized -match "(^|/)(docker-logs-last-run|logs)(/|$)" -or $normalized -match "\.log$" -or $normalized -match "_log\.txt$") {
        $errors.Add("Tracked runtime log artifact: $path")
    }
}

$generatedPasswordFiles = git -C $RepoRoot grep -I -l -E "Using generated security password:\s+[A-Za-z0-9+/=_-]{12,}" -- . 2>$null
if ($LASTEXITCODE -eq 0 -and $generatedPasswordFiles) {
    foreach ($match in $generatedPasswordFiles) {
        $errors.Add("Tracked file contains generated Spring security password output: $match")
    }
} elseif ($LASTEXITCODE -gt 1) {
    Fail "git grep failed while scanning for generated password output."
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Repository artifact hygiene validation passed."
