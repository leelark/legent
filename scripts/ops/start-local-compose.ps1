param(
    [string]$EnvFile = ".env.example",
    [string[]]$ComposeFile = @(),
    [switch]$NoBuild,
    [switch]$SkipHealth,
    [switch]$ForceRecreate,
    [int]$WaitSeconds = 300,
    [int]$PollSeconds = 10,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-EnvValues {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path $Path)) {
        return $values
    }

    Get-Content -Path $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) {
            return
        }
        $key = $line.Substring(0, $idx)
        $value = $line.Substring($idx + 1)
        $values[$key] = $value
    }
    return $values
}

function Test-PlaceholderLikeSecret {
    param([AllowNull()][string]$Value)

    if ($null -eq $Value) {
        return $true
    }
    $normalized = $Value.Trim().ToLowerInvariant()
    return $normalized.Length -eq 0 `
        -or $normalized.Contains("change_me") `
        -or $normalized.Contains("changeme") `
        -or $normalized.Contains("replace") `
        -or $normalized.Contains("placeholder") `
        -or $normalized.Contains("example") `
        -or $normalized.Contains("dummy") `
        -or $normalized.Contains("dev-token") `
        -or $normalized -eq "password" `
        -or $normalized -eq "minioadmin"
}

function Test-ConfiguredSecret {
    param([AllowNull()][string]$Value)

    if ($null -eq $Value) {
        return $false
    }
    $trimmed = $Value.Trim()
    if ($trimmed.Length -lt 32) {
        return $false
    }
    if (Test-PlaceholderLikeSecret $trimmed) {
        return $false
    }
    $distinctChars = @($trimmed.ToCharArray() | Select-Object -Unique).Count
    if ($distinctChars -lt 8) {
        return $false
    }
    $hasLetter = $trimmed -match "[A-Za-z]"
    $hasDigitOrSymbol = $trimmed -match "[^A-Za-z]"
    return $hasLetter -and $hasDigitOrSymbol
}

function Test-Base64Salt {
    param([AllowNull()][string]$Value)

    if (Test-PlaceholderLikeSecret $Value) {
        return $false
    }
    try {
        $decoded = [Convert]::FromBase64String($Value.Trim())
        return $decoded.Length -ge 16
    } catch {
        return $false
    }
}

function New-RandomBase64 {
    param([int]$ByteCount)

    $bytes = New-Object byte[] $ByteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Get-ConfiguredValue {
    param(
        [hashtable]$Values,
        [string]$Name
    )

    $processValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        return $processValue
    }
    if ($Values.ContainsKey($Name)) {
        return [string]$Values[$Name]
    }
    return ""
}

function Set-TransientOverrideIfNeeded {
    param(
        [hashtable]$Values,
        [string]$Name,
        [scriptblock]$Validator,
        [int]$RandomByteCount
    )

    $current = Get-ConfiguredValue -Values $Values -Name $Name
    if (& $Validator $current) {
        return $false
    }
    [Environment]::SetEnvironmentVariable($Name, (New-RandomBase64 -ByteCount $RandomByteCount), "Process")
    Write-Host "Generated transient local override for $Name."
    return $true
}

function New-ComposeBaseArguments {
    param(
        [string]$ResolvedEnvFile,
        [string[]]$ResolvedComposeFiles
    )

    $arguments = @("compose")
    if (-not [string]::IsNullOrWhiteSpace($ResolvedEnvFile)) {
        $arguments += @("--env-file", $ResolvedEnvFile)
    }
    foreach ($file in @($ResolvedComposeFiles)) {
        if (-not [string]::IsNullOrWhiteSpace($file)) {
            $arguments += @("-f", $file)
        }
    }
    return $arguments
}

if ($SelfTest) {
    if (Test-ConfiguredSecret "replace_with_32_plus_character_internal_api_token") {
        throw "placeholder secret should be rejected"
    }
    if (-not (Test-ConfiguredSecret "LegentLocalRuntimeSecret-20260526-abcdef")) {
        throw "valid local secret fixture should pass"
    }
    if (Test-Base64Salt "replace_with_base64_encoded_salt") {
        throw "placeholder salt should be rejected"
    }
    $salt = New-RandomBase64 -ByteCount 24
    if (-not (Test-Base64Salt $salt)) {
        throw "random Base64 salt fixture should pass"
    }
    if ($EnvFile -ne ".env.example") {
        throw "default env file should be .env.example"
    }
    Write-Host "Local compose starter self-test passed."
    exit 0
}

$resolvedEnvFile = $EnvFile
if ([string]::IsNullOrWhiteSpace($resolvedEnvFile)) {
    throw "EnvFile must not be empty."
}
if (-not (Test-Path $resolvedEnvFile)) {
    throw "Missing env file: $resolvedEnvFile"
}
$envValues = Read-EnvValues -Path $resolvedEnvFile
$usedTransientOverrides = $false
$usedTransientOverrides = (Set-TransientOverrideIfNeeded -Values $envValues -Name "LEGENT_INTERNAL_API_TOKEN" -Validator ${function:Test-ConfiguredSecret} -RandomByteCount 48) -or $usedTransientOverrides
$usedTransientOverrides = (Set-TransientOverrideIfNeeded -Values $envValues -Name "LEGENT_DELIVERY_CREDENTIAL_KEY" -Validator ${function:Test-ConfiguredSecret} -RandomByteCount 48) -or $usedTransientOverrides
$usedTransientOverrides = (Set-TransientOverrideIfNeeded -Values $envValues -Name "LEGENT_DELIVERY_ENCRYPTION_SALT" -Validator ${function:Test-Base64Salt} -RandomByteCount 24) -or $usedTransientOverrides

$composeArgs = New-ComposeBaseArguments -ResolvedEnvFile $resolvedEnvFile -ResolvedComposeFiles $ComposeFile
$composeArgs += @("up", "-d")
if (-not $NoBuild) {
    $composeArgs += "--build"
}
if ($ForceRecreate -or $usedTransientOverrides) {
    $composeArgs += "--force-recreate"
}

Write-Host "Starting local Compose stack with env file $resolvedEnvFile."
& docker @composeArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if (-not $SkipHealth) {
    & (Join-Path $PSScriptRoot "validate-compose-health.ps1") -ComposeEnvFile $resolvedEnvFile -ComposeFile $ComposeFile -WaitSeconds $WaitSeconds -PollSeconds $PollSeconds
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
