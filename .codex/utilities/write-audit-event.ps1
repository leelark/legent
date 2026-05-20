param(
    [Parameter(Mandatory = $true)][string]$EventType,
    [Parameter(Mandatory = $true)][string]$Actor,
    [string]$ThreadId = "",
    [string]$WorkItemId = "",
    [string]$Module = "overall",
    [string[]]$Files = @(),
    [string[]]$Validation = @(),
    [string]$Source = "",
    [Parameter(Mandatory = $true)][string]$Summary,
    [string]$NextAction = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$forbidden = "password|secret|token|private key|BEGIN RSA|BEGIN OPENSSH|\.env"
if ($Summary -match $forbidden -or $NextAction -match $forbidden) {
    Write-Error "Audit event appears to contain sensitive material. Refusing to write."
    exit 1
}

$date = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
$dir = ".codex/audit/events"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$path = Join-Path $dir "$date.jsonl"
$event = [ordered]@{
    timestamp = (Get-Date).ToUniversalTime().ToString("o")
    eventType = $EventType
    actor = $Actor
    threadId = $ThreadId
    workItemId = $WorkItemId
    module = $Module
    files = @($Files)
    validation = @($Validation)
    source = $Source
    summary = $Summary
    nextAction = $NextAction
}
($event | ConvertTo-Json -Compress -Depth 8) + "`n" | Add-Content -Path $path -Encoding UTF8
Write-Host "Wrote audit event to $path"
