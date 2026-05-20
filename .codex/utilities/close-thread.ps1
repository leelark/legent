param(
    [Parameter(Mandatory = $true)][string]$ThreadId,
    [ValidateSet("DONE", "ARCHIVED", "PAUSED", "BLOCKED")][string]$Status = "DONE",
    [string]$NextAction = "Thread closed."
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$path = ".codex/threads/thread-registry.json"
if (-not (Test-Path $path)) { Write-Error "Missing thread registry: $path"; exit 1 }
$registry = Get-Content -Path $path -Raw | ConvertFrom-Json
$thread = @($registry.threads) | Where-Object { $_.threadId -eq $ThreadId } | Select-Object -First 1
if (-not $thread) { Write-Error "Thread is not registered: $ThreadId"; exit 1 }

$now = (Get-Date).ToUniversalTime().ToString("o")
$thread.status = $Status
$thread.heartbeatAt = $now
$thread.lastUpdated = $now
$thread.activeAgents = @()
$thread.nextAction = $NextAction

if ($registry.coordinatorThreadId -eq $ThreadId -and $Status -in @("DONE", "ARCHIVED")) {
    $registry.coordinatorThreadId = $null
}

$registry | ConvertTo-Json -Depth 12 | Set-Content -Path $path -Encoding UTF8
Write-Host "Thread $ThreadId moved to $Status."
