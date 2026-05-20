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
. (Join-Path $PSScriptRoot "codex-state.ps1")

$forbidden = "password|secret|token|private key|BEGIN RSA|BEGIN OPENSSH|\.env"
if ($Summary -match $forbidden -or $NextAction -match $forbidden) {
    Write-Error "Audit event appears to contain sensitive material. Refusing to write."
    exit 1
}

$date = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
$dir = ".codex/audit/events"
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
Invoke-CodexStateMutation -Name "write-audit-event" -ScriptBlock {
    Add-CodexJsonLine -Object $event -Path $path -Depth 8
}
Write-Host "Wrote audit event to $path"
