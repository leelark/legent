param(
    [string] $EnvFile = ".env",
    [switch] $AllowPlaceholders
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$envPath = Join-Path $repoRoot $EnvFile

if (-not (Test-Path $envPath)) {
    throw "Environment file not found: $envPath"
}

function Read-DotEnv([string] $Path) {
    $values = @{}
    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) {
            continue
        }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $values[$key] = $value
    }
    return $values
}

$values = Read-DotEnv $envPath
$required = @(
    "DB_USER",
    "DB_PASSWORD",
    "LEGENT_SECURITY_JWT_SECRET",
    "CORS_ALLOWED_ORIGINS",
    "NEXT_PUBLIC_API_BASE_URL",
    "API_GATEWAY_URL",
    "TRACKING_BASE_URL",
    "LEGENT_TRACKING_SIGNING_KEY",
    "LEGENT_DELIVERY_CREDENTIAL_KEY",
    "LEGENT_DELIVERY_ENCRYPTION_SALT",
    "LEGENT_INTERNAL_API_TOKEN"
)

$minimumLengths = @{
    LEGENT_SECURITY_JWT_SECRET = 64
    LEGENT_TRACKING_SIGNING_KEY = 32
    LEGENT_DELIVERY_CREDENTIAL_KEY = 32
    LEGENT_DELIVERY_ENCRYPTION_SALT = 16
    LEGENT_INTERNAL_API_TOKEN = 32
}

$errors = New-Object System.Collections.Generic.List[string]
foreach ($key in $required) {
    if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string] $values[$key])) {
        $errors.Add("$key is required")
        continue
    }
    $value = [string] $values[$key]
    if (-not $AllowPlaceholders -and $value -match "^(replace_with_|change_me|changeme)") {
        $errors.Add("$key still uses a placeholder value")
    }
    if ($minimumLengths.ContainsKey($key) -and $value.Length -lt $minimumLengths[$key]) {
        $errors.Add("$key must be at least $($minimumLengths[$key]) characters")
    }
}

if ($errors.Count -gt 0) {
    throw "Environment validation failed:`n - $($errors -join "`n - ")"
}

Write-Host "Environment validation passed for $EnvFile"
