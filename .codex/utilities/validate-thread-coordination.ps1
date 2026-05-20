param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Path-Overlaps([string]$A, [string]$B) {
    $aNorm = $A.Trim().TrimEnd("\", "/").ToLowerInvariant()
    $bNorm = $B.Trim().TrimEnd("\", "/").ToLowerInvariant()
    if (-not $aNorm -or -not $bNorm) { return $false }
    if ($aNorm -eq "**" -or $bNorm -eq "**") { return $true }
    $aBase = $aNorm.Replace("/**", "").Replace("\**", "")
    $bBase = $bNorm.Replace("/**", "").Replace("\**", "")
    return ($aBase -eq $bBase -or $aBase.StartsWith($bBase + "\") -or $bBase.StartsWith($aBase + "\") -or $aBase.StartsWith($bBase + "/") -or $bBase.StartsWith($aBase + "/"))
}

function Is-SharedCoordinationPath([string]$Path) {
    $p = $Path.Trim().Replace("\", "/").ToLowerInvariant()
    return ($p -like ".codex/*" -or $p -eq ".codex/**" -or $p -eq "shared/**")
}

$threadPath = ".codex/threads/thread-registry.json"
$teamPath = ".codex/teams/module-team-registry.json"
$leasePath = ".codex/worktrees/leases/active-leases.json"

if (-not (Test-Path $threadPath)) { Fail "Missing thread registry." }
if (-not (Test-Path $teamPath)) { Fail "Missing module team registry." }
if (-not (Test-Path $leasePath)) { Fail "Missing lease registry." }

$threads = Get-Content -Path $threadPath -Raw | ConvertFrom-Json
$teams = Get-Content -Path $teamPath -Raw | ConvertFrom-Json
$leases = Get-Content -Path $leasePath -Raw | ConvertFrom-Json
$teamModules = @($teams.teams | ForEach-Object { $_.module })
$ids = @{}

foreach ($thread in @($threads.threads)) {
    if ($ids.ContainsKey([string]$thread.threadId)) { Fail "Duplicate thread id: $($thread.threadId)" }
    $ids[[string]$thread.threadId] = $true
    if ($teamModules -notcontains [string]$thread.module) { Fail "Thread $($thread.threadId) has unknown module $($thread.module)" }
    if (@($thread.activeAgents).Count -gt [int]$thread.maxParallelAgents) { Fail "Thread $($thread.threadId) exceeds maxParallelAgents." }
    if ([int]$thread.maxParallelAgents -gt 6) { Fail "Thread $($thread.threadId) maxParallelAgents exceeds 6." }
    if (-not $thread.heartbeatAt) { Fail "Thread $($thread.threadId) missing heartbeatAt." }
}

$activeThreads = @($threads.threads | Where-Object { $_.status -in @("ACTIVE", "REVIEW") })
for ($i = 0; $i -lt $activeThreads.Count; $i++) {
    for ($j = $i + 1; $j -lt $activeThreads.Count; $j++) {
        $left = $activeThreads[$i]
        $right = $activeThreads[$j]
        if ($left.threadRole -eq "OVERALL" -or $right.threadRole -eq "OVERALL") { continue }
        foreach ($lp in @($left.allowedPaths)) {
            foreach ($rp in @($right.allowedPaths)) {
                if ((Path-Overlaps $lp $rp) -and -not (Is-SharedCoordinationPath $lp) -and -not (Is-SharedCoordinationPath $rp)) {
                    Fail "Active module threads overlap: $($left.threadId) path $lp and $($right.threadId) path $rp"
                }
            }
        }
    }
}

foreach ($lease in @($leases.leases)) {
    if (-not $lease.threadId -and -not $lease.owner) { Fail "Lease missing threadId or owner." }
    if (@($lease.filesInScope).Count -eq 0) { Fail "Lease missing filesInScope." }
}

Write-Host "Thread coordination validation passed for $(@($threads.threads).Count) registered threads."
