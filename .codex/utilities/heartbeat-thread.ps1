param(
    [Parameter(Mandatory = $true)][string]$ThreadId,
    [string]$Status = "ACTIVE",
    [string]$NextAction = "",
    [string]$CheckpointId = "",
    [string[]]$ActiveAgents = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$path = ".codex/threads/thread-registry.json"
if (-not (Test-Path $path)) { Write-Error "Missing thread registry: $path"; exit 1 }
$registry = Get-Content -Path $path -Raw | ConvertFrom-Json
$thread = @($registry.threads) | Where-Object { $_.threadId -eq $ThreadId } | Select-Object -First 1
if (-not $thread) { Write-Error "Thread is not registered: $ThreadId"; exit 1 }
if (@($ActiveAgents).Count -gt [int]$thread.maxParallelAgents) { Write-Error "ActiveAgents exceeds thread maxParallelAgents."; exit 1 }

$now = (Get-Date).ToUniversalTime().ToString("o")
$thread.status = $Status
$thread.heartbeatAt = $now
$thread.lastUpdated = $now
if ($NextAction) { $thread.nextAction = $NextAction }
if ($CheckpointId) { $thread.checkpointId = $CheckpointId }
if ($PSBoundParameters.ContainsKey("ActiveAgents")) { $thread.activeAgents = @($ActiveAgents) }

$registry | ConvertTo-Json -Depth 12 | Set-Content -Path $path -Encoding UTF8
Write-Host "Heartbeat recorded for $ThreadId at $now."
