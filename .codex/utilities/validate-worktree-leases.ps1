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
$seenIds = @{}
$now = (Get-Date).ToUniversalTime()

function Normalize-Scope([string]$Scope) {
    return $Scope.Trim().TrimEnd("\", "/").Replace("/", "\").ToLowerInvariant().Replace("\**", "")
}

function Scopes-Overlap([string]$Left, [string]$Right) {
    $l = Normalize-Scope $Left
    $r = Normalize-Scope $Right
    if (-not $l -or -not $r) { return $false }
    if ($l -eq $r) { return $true }
    return ($l.StartsWith($r + "\") -or $r.StartsWith($l + "\"))
}

foreach ($lease in $leases) {
    if (-not $lease.leaseId) { Fail "Active lease missing leaseId." }
    if ($seenIds.ContainsKey([string]$lease.leaseId)) { Fail "Duplicate active leaseId: $($lease.leaseId)" }
    $seenIds[[string]$lease.leaseId] = $true
    if (-not $lease.threadId) { Fail "Active lease missing threadId." }
    if (-not $lease.owner) { Fail "Active lease missing owner." }
    if (-not $lease.workItemId) { Fail "Active lease missing workItemId." }
    if (@($lease.filesInScope).Count -eq 0) { Fail "Active lease $($lease.leaseId) missing filesInScope." }
    if (-not $lease.expiresAt) { Fail "Active lease $($lease.leaseId) missing expiresAt." }
    if (-not $lease.heartbeatAt) { Fail "Active lease $($lease.leaseId) missing heartbeatAt." }
    $expiresAt = [datetime]::Parse([string]$lease.expiresAt).ToUniversalTime()
    if ($expiresAt -lt $now) { Fail "Active lease $($lease.leaseId) expired at $($lease.expiresAt)." }
    foreach ($scope in @($lease.filesInScope)) {
        if (-not $scope) { continue }
        foreach ($existingScope in $seen.Keys) {
            if (Scopes-Overlap $existingScope ([string]$scope)) {
                Fail "Overlapping active lease for ${scope}: $($seen[$existingScope]) and $($lease.owner)"
            }
        }
        $seen[[string]$scope] = $lease.owner
    }
}

Write-Host "Worktree lease validation passed for $($leases.Count) active leases."
