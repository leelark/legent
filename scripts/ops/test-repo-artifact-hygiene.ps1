param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"

function New-TempDirectory {
    $path = Join-Path ([System.IO.Path]::GetTempPath()) ("legent-artifact-hygiene-test-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $path -Force | Out-Null
    return $path
}

function Write-TextFile {
    param(
        [Parameter(Mandatory = $true)] [string] $Path,
        [Parameter(Mandatory = $true)] [string] $Content
    )

    $directory = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)] [string] $WorkingDirectory,
        [Parameter(Mandatory = $true)] [string[]] $Arguments
    )

    Push-Location $WorkingDirectory
    try {
        $output = & git @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "git $($Arguments -join ' ') failed in ${WorkingDirectory}: $($output -join '; ')"
        }
        return @($output)
    } finally {
        Pop-Location
    }
}

function New-TestRepo {
    param(
        [Parameter(Mandatory = $true)] [string] $Root,
        [Parameter(Mandatory = $true)] [string] $Name
    )

    $repo = Join-Path $Root $Name
    New-Item -ItemType Directory -Path $repo -Force | Out-Null
    Invoke-Git -WorkingDirectory $repo -Arguments @("init", "--quiet") | Out-Null
    Invoke-Git -WorkingDirectory $repo -Arguments @("config", "user.email", "artifact-hygiene-test@example.invalid") | Out-Null
    Invoke-Git -WorkingDirectory $repo -Arguments @("config", "user.name", "Artifact Hygiene Test") | Out-Null
    return $repo
}

function Add-AllFiles {
    param([Parameter(Mandatory = $true)] [string] $Repo)

    Invoke-Git -WorkingDirectory $Repo -Arguments @("add", ".") | Out-Null
}

function Invoke-ExpectSuccess {
    param(
        [Parameter(Mandatory = $true)] [string] $Name,
        [Parameter(Mandatory = $true)] [string] $TestRepo,
        [Parameter(Mandatory = $true)] [string] $ValidatorPath
    )

    try {
        & $ValidatorPath -RepoRoot $TestRepo *> $null
        Write-Host "PASS success: $Name"
    } catch {
        throw "Expected success for '$Name', but validation failed: $($_.Exception.Message)"
    }
}

function Invoke-ExpectFailure {
    param(
        [Parameter(Mandatory = $true)] [string] $Name,
        [Parameter(Mandatory = $true)] [string] $TestRepo,
        [Parameter(Mandatory = $true)] [string] $ValidatorPath,
        [Parameter(Mandatory = $true)] [string] $ExpectedPattern
    )

    try {
        & $ValidatorPath -RepoRoot $TestRepo *> $null
        throw "Expected failure did not occur"
    } catch {
        if ($_.Exception.Message -eq "Expected failure did not occur") {
            throw "Expected failure for '$Name', but validation passed."
        }
        if ($_.Exception.Message -notmatch $ExpectedPattern) {
            throw "Expected failure for '$Name' to match '$ExpectedPattern', got: $($_.Exception.Message)"
        }
        Write-Host "PASS failure: $Name"
    }
}

$validatorPath = Join-Path $RepoRoot "scripts\ops\validate-repo-artifact-hygiene.ps1"
if (-not (Test-Path -LiteralPath $validatorPath -PathType Leaf)) {
    throw "Validator script not found: $validatorPath"
}

$tempRoot = New-TempDirectory

try {
    $sourceReferenceRepo = New-TestRepo -Root $tempRoot -Name "source-reference"
    Write-TextFile -Path (Join-Path $sourceReferenceRepo "scripts\ops\validator-source.ps1") -Content 'git grep -n -I -F "Using generated security password" --'
    Write-TextFile -Path (Join-Path $sourceReferenceRepo ".codex\memory\bug-history.md") -Content 'Regression note: validator used to self-match the literal "Using generated security password" in source text.'
    Add-AllFiles -Repo $sourceReferenceRepo
    Invoke-ExpectSuccess -Name "source and prose references to the detection literal" -TestRepo $sourceReferenceRepo -ValidatorPath $validatorPath

    $passwordOutputRepo = New-TestRepo -Root $tempRoot -Name "password-output"
    $generatedPasswordLine = ("Using generated security " + "password: self-test-password")
    Write-TextFile -Path (Join-Path $passwordOutputRepo "startup-output.txt") -Content $generatedPasswordLine
    Add-AllFiles -Repo $passwordOutputRepo
    Invoke-ExpectFailure -Name "tracked generated Spring password output" -TestRepo $passwordOutputRepo -ValidatorPath $validatorPath -ExpectedPattern "generated Spring security password output"

    $runtimeLogRepo = New-TestRepo -Root $tempRoot -Name "runtime-log"
    Write-TextFile -Path (Join-Path $runtimeLogRepo "logs\application.log") -Content "Application started"
    Add-AllFiles -Repo $runtimeLogRepo
    Invoke-ExpectFailure -Name "tracked runtime log artifact" -TestRepo $runtimeLogRepo -ValidatorPath $validatorPath -ExpectedPattern "runtime log artifact"

    Write-Host "Repository artifact hygiene validator tests passed."
} finally {
    $resolvedTempRoot = (Resolve-Path -LiteralPath $tempRoot).Path
    $systemTempRoot = [System.IO.Path]::GetTempPath()
    if (-not $resolvedTempRoot.StartsWith($systemTempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove unexpected test directory: $resolvedTempRoot"
    }
    Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
