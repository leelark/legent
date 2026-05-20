param(
    [Parameter(Mandatory = $true)][string]$ThreadId,
    [Parameter(Mandatory = $true)][string]$WorkItemId,
    [Parameter(Mandatory = $true)][string[]]$FilesInScope,
    [int]$TtlMinutes = 240
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$leasePath = ".codex/worktrees/leases/active-leases.json"
$threadPath = ".codex/threads/thread-registry.json"
$leases = Get-Content -Path $leasePath -Raw | ConvertFrom-Json
$threads = Get-Content -Path $threadPath -Raw | ConvertFrom-Json
$thread = @($threads.threads) | Where-Object { $_.threadId -eq $ThreadId } | Select-Object -First 1
if (-not $thread) { Write-Error "Thread is not registered: $ThreadId"; exit 1 }

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

foreach ($lease in @($leases.leases | Where-Object { -not $_.status -or $_.status -eq "ACTIVE" })) {
    foreach ($existingScope in @($lease.filesInScope)) {
        foreach ($newScope in $FilesInScope) {
            if (Scopes-Overlap ([string]$existingScope) ([string]$newScope)) {
                Write-Error "Requested lease $newScope overlaps active lease $existingScope owned by $($lease.owner)."
                exit 1
            }
        }
    }
}

$leaseId = "$ThreadId-$WorkItemId-" + (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$record = [ordered]@{
    leaseId = $leaseId
    threadId = $ThreadId
    owner = $thread.owner
    workItemId = $WorkItemId
    agentId = ""
    worktreeId = ""
    branch = ""
    status = "ACTIVE"
    filesInScope = @($FilesInScope)
    writeGlobs = @($FilesInScope)
    readOnly = $false
    baseCommit = (git rev-parse HEAD)
    acquiredAt = (Get-Date).ToUniversalTime().ToString("o")
    expiresAt = (Get-Date).ToUniversalTime().AddMinutes($TtlMinutes).ToString("o")
    heartbeatAt = (Get-Date).ToUniversalTime().ToString("o")
}

$leases.leases = @($leases.leases) + $record
$thread.leaseIds = @($thread.leaseIds) + $leaseId
$thread.lastUpdated = (Get-Date).ToUniversalTime().ToString("o")

$leases | ConvertTo-Json -Depth 12 | Set-Content -Path $leasePath -Encoding UTF8
$threads | ConvertTo-Json -Depth 12 | Set-Content -Path $threadPath -Encoding UTF8
& .codex/utilities/validate-worktree-leases.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Created lease $leaseId for $ThreadId."
