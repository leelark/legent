param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

$path = ".codex/worktrees/leases/active-leases.json"
if (-not (Test-Path $path)) { Fail "Missing active leases file: $path" }
$state = Get-Content -Path $path -Raw | ConvertFrom-Json
$leases = @(@($state.leases) | Where-Object { -not $_.status -or $_.status -eq "ACTIVE" })
$seen = @{}

foreach ($lease in $leases) {
    if (-not $lease.owner) { Fail "Active lease missing owner." }
    if (-not $lease.workItemId) { Fail "Active lease missing workItemId." }
    foreach ($scope in @($lease.filesInScope)) {
        if (-not $scope) { continue }
        $normalized = ([string]$scope).Trim().TrimEnd("\", "/").ToLowerInvariant()
        if ($seen.ContainsKey($normalized)) {
            Fail "Overlapping active lease for ${scope}: $($seen[$normalized]) and $($lease.owner)"
        }
        $seen[$normalized] = $lease.owner
    }
}

Write-Host "Worktree lease validation passed for $($leases.Count) active leases."
