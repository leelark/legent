param(
    [string]$EnvFile = ".env.example",
    [switch]$AllowPlaceholders
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

if (-not (Test-Path $EnvFile)) { Fail "Missing env file: $EnvFile" }

$required = @(
    "DB_USER",
    "DB_PASSWORD",
    "LEGENT_SECURITY_JWT_SECRET",
    "CORS_ALLOWED_ORIGINS",
    "FRONTEND_HOST_PORT",
    "MINIO_ACCESS_KEY",
    "MINIO_SECRET_KEY",
    "NEXT_PUBLIC_API_BASE_URL",
    "API_GATEWAY_URL",
    "TRACKING_BASE_URL",
    "LEGENT_TRACKING_SIGNING_KEY",
    "LEGENT_DELIVERY_CREDENTIAL_KEY",
    "LEGENT_DELIVERY_ENCRYPTION_SALT",
    "LEGENT_INTERNAL_API_TOKEN"
)

$values = @{}
Get-Content -Path $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx)
    $value = $line.Substring($idx + 1)
    $values[$key] = $value
}

$errors = New-Object System.Collections.Generic.List[string]
foreach ($key in $required) {
    if (-not $values.ContainsKey($key)) {
        $errors.Add("Missing required key: $key")
        continue
    }
    if ([string]::IsNullOrWhiteSpace($values[$key])) {
        $errors.Add("Required key has empty value: $key")
    }
    if (-not $AllowPlaceholders -and $values[$key] -match "replace_with|placeholder|changeme") {
        $errors.Add("Placeholder value found for key: $key")
    }
}

if ($values.ContainsKey("LEGENT_SECURITY_JWT_SECRET") -and $values["LEGENT_SECURITY_JWT_SECRET"].Length -lt 32) {
    $errors.Add("LEGENT_SECURITY_JWT_SECRET is shorter than 32 characters")
}
if ($values.ContainsKey("LEGENT_TRACKING_SIGNING_KEY") -and $values["LEGENT_TRACKING_SIGNING_KEY"].Length -lt 32) {
    $errors.Add("LEGENT_TRACKING_SIGNING_KEY is shorter than 32 characters")
}
if ($values.ContainsKey("LEGENT_INTERNAL_API_TOKEN") -and $values["LEGENT_INTERNAL_API_TOKEN"].Length -lt 32) {
    $errors.Add("LEGENT_INTERNAL_API_TOKEN is shorter than 32 characters")
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Environment validation passed for $EnvFile"
