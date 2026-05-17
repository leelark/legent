param(
    [string] $ValidatorPath = (Join-Path $PSScriptRoot "validate-audience-data-extension-workspace-mapping.ps1")
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Test-Path -LiteralPath $ValidatorPath -PathType Leaf)) {
    throw "Validator script not found: $ValidatorPath"
}

$tempRoot = [System.IO.Path]::GetTempPath()
$testRoot = Join-Path $tempRoot ("legent-audience-mapping-tests-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $testRoot | Out-Null

function Write-TestFile([string] $Name, [string] $Content) {
    $path = Join-Path $testRoot $Name
    Set-Content -LiteralPath $path -Value $Content -NoNewline -Encoding utf8
    return $path
}

function Invoke-ExpectSuccess([string] $Name, [hashtable] $Parameters) {
    try {
        & $ValidatorPath @Parameters *> $null
        Write-Host "PASS success: $Name"
    }
    catch {
        throw "Expected success for '$Name', but validation failed: $($_.Exception.Message)"
    }
}

function Invoke-ExpectFailure([string] $Name, [hashtable] $Parameters, [string] $ExpectedPattern) {
    try {
        & $ValidatorPath @Parameters *> $null
        throw "Expected failure did not occur"
    }
    catch {
        if ($_.Exception.Message -eq "Expected failure did not occur") {
            throw "Expected failure for '$Name', but validation passed."
        }
        if ($_.Exception.Message -notmatch $ExpectedPattern) {
            throw "Expected failure for '$Name' to match '$ExpectedPattern', got: $($_.Exception.Message)"
        }
        Write-Host "PASS failure: $Name"
    }
}

try {
    $validCsv = Write-TestFile "valid.csv" @'
tenant_id,data_extension_id,target_workspace_id,reviewed_by,reviewed_at,review_ticket
tenant-a,de-1,workspace-a,operator@example.com,2026-05-16T00:00:00Z,CHANGE-123
tenant-b,de-2,workspace-b,operator@example.com,2026-05-16T00:00:00Z,CHANGE-123
'@
    $sqlOutput = Join-Path $testRoot "mapping.sql"
    Invoke-ExpectSuccess -Name "valid CSV with SQL output" -Parameters @{ InputPath = $validCsv; SqlOutputPath = $sqlOutput }
    $sql = Get-Content -Raw -LiteralPath $sqlOutput
    if ($sql -notmatch "public\.audience_data_extension_workspace_mapping_review" -or $sql -notmatch "INSERT INTO") {
        throw "Expected generated SQL to create and populate audience_data_extension_workspace_mapping_review."
    }

    $validJson = Write-TestFile "valid.json" @'
{
  "mappings": [
    {
      "tenant_id": "tenant-a",
      "data_extension_id": "de-json-1",
      "target_workspace_id": "workspace-json-a",
      "review_ticket": "CHANGE-123"
    }
  ]
}
'@
    Invoke-ExpectSuccess -Name "valid JSON with reviewed metadata parameters" -Parameters @{
        InputPath = $validJson
        ReviewedBy = "operator@example.com"
        ReviewedAt = "2026-05-16T00:00:00Z"
    }

    $placeholderCsv = Write-TestFile "placeholder.csv" @'
tenant_id,data_extension_id,target_workspace_id,reviewed_by,reviewed_at
tenant-a,de-1,workspace-default,operator@example.com,2026-05-16T00:00:00Z
'@
    Invoke-ExpectFailure -Name "placeholder workspace id" -Parameters @{ InputPath = $placeholderCsv } -ExpectedPattern "placeholder"

    $duplicateCsv = Write-TestFile "duplicate.csv" @'
tenant_id,data_extension_id,target_workspace_id,reviewed_by,reviewed_at
tenant-a,de-1,workspace-a,operator@example.com,2026-05-16T00:00:00Z
tenant-b,de-1,workspace-b,operator@example.com,2026-05-16T00:00:00Z
'@
    Invoke-ExpectFailure -Name "duplicate data_extension_id" -Parameters @{ InputPath = $duplicateCsv } -ExpectedPattern "duplicate data_extension_id"

    $missingTargetCsv = Write-TestFile "missing-target.csv" @'
tenant_id,data_extension_id,target_workspace_id,reviewed_by,reviewed_at
tenant-a,de-1,,operator@example.com,2026-05-16T00:00:00Z
'@
    Invoke-ExpectFailure -Name "missing target_workspace_id" -Parameters @{ InputPath = $missingTargetCsv } -ExpectedPattern "target_workspace_id is required"

    $malformedCsv = Write-TestFile "malformed.csv" @'
tenant_id,data_extension_id,target_workspace_id,reviewed_by,reviewed_at
tenant-a,de-1,workspace-a,operator@example.com,2026-05-16T00:00:00Z,extra
'@
    Invoke-ExpectFailure -Name "malformed CSV" -Parameters @{ InputPath = $malformedCsv } -ExpectedPattern "Malformed CSV"

    $malformedJson = Write-TestFile "malformed.json" '{ "mappings": [ { "tenant_id": "tenant-a" '
    Invoke-ExpectFailure -Name "malformed JSON" -Parameters @{ InputPath = $malformedJson } -ExpectedPattern "Malformed JSON"

    Write-Host "Audience data-extension workspace mapping validator tests passed."
}
finally {
    $resolvedTestRoot = (Resolve-Path -LiteralPath $testRoot).Path
    if (-not $resolvedTestRoot.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove unexpected test directory: $resolvedTestRoot"
    }
    Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
}
