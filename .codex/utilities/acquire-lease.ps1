param(
    [Parameter(Mandatory = $true)][string]$ThreadId,
    [Parameter(Mandatory = $true)][string]$WorkItemId,
    [Parameter(Mandatory = $true)][string[]]$FilesInScope,
    [string]$AgentId = "",
    [string]$WorktreeId = "",
    [string]$Branch = "",
    [switch]$ReadOnly,
    [int]$TtlMinutes = 240
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$threadPath = ".codex/threads/thread-registry.json"
$leasePath = ".codex/worktrees/leases/active-leases.json"
$threads = Get-Content -Path $threadPath -Raw | ConvertFrom-Json
$leases = Get-Content -Path $leasePath -Raw | ConvertFrom-Json
$thread = @($threads.threads) | Where-Object { $_.threadId -eq $ThreadId } | Select-Object -First 1
if (-not $thread) { Write-Error "Thread is not registered: $ThreadId"; exit 1 }
if (@($FilesInScope).Count -eq 0) { Write-Error "FilesInScope must not be empty."; exit 1 }

$leaseId = "$ThreadId-$WorkItemId-" + (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$now = (Get-Date).ToUniversalTime()
$record = [ordered]@{
    leaseId = $leaseId
    threadId = $ThreadId
    workItemId = $WorkItemId
    agentId = $AgentId
    owner = $thread.owner
    worktreeId = $WorktreeId
    branch = $Branch
    filesInScope = @($FilesInScope)
    writeGlobs = @($FilesInScope)
    readOnly = [bool]$ReadOnly
    status = "ACTIVE"
    baseCommit = (git rev-parse HEAD)
    acquiredAt = $now.ToString("o")
    expiresAt = $now.AddMinutes($TtlMinutes).ToString("o")
    heartbeatAt = $now.ToString("o")
}

$leases.leases = @($leases.leases) + $record
$thread.leaseIds = @($thread.leaseIds) + $leaseId
$thread.lastUpdated = $now.ToString("o")
$thread.heartbeatAt = $now.ToString("o")

$leases | ConvertTo-Json -Depth 12 | Set-Content -Path $leasePath -Encoding UTF8
$threads | ConvertTo-Json -Depth 12 | Set-Content -Path $threadPath -Encoding UTF8

& .codex/utilities/validate-worktree-leases.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& .codex/utilities/write-audit-event.ps1 -EventType LEASE_ACQUIRED -Actor $thread.owner -ThreadId $ThreadId -WorkItemId $WorkItemId -Module $thread.module -Files $FilesInScope -Summary "Lease acquired for scoped module work." -NextAction "Proceed with checkpointed work."
Write-Host "Acquired lease $leaseId"
