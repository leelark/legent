param(
    [switch]$FailOnUnregistered = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Normalize-PathText([string]$Path) {
    return $Path.Replace("\", "/").TrimEnd("/").ToLowerInvariant()
}

$registryPath = ".codex/worktrees/worktree-registry.json"
if (-not (Test-Path $registryPath)) { Write-Error "Missing worktree registry."; exit 1 }
$registry = Get-Content -Path $registryPath -Raw | ConvertFrom-Json

$known = New-Object System.Collections.Generic.HashSet[string]
foreach ($record in @($registry.activeWorktrees) + @($registry.archivedWorktrees) + @($registry.observedExternalWorktrees)) {
    if ($record.path) { [void]$known.Add((Normalize-PathText ([string]$record.path))) }
}

$gitLines = git worktree list --porcelain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$currentPath = $null
$unregistered = New-Object System.Collections.Generic.List[string]
foreach ($line in $gitLines) {
    if ($line -match "^worktree\s+(.+)$") {
        $currentPath = $Matches[1]
        $normalized = Normalize-PathText $currentPath
        if (-not $known.Contains($normalized)) {
            $repoRoot = Normalize-PathText (Resolve-Path ".").Path
            if ($normalized -ne $repoRoot) {
                $unregistered.Add($currentPath)
            }
        }
    }
}

if ($unregistered.Count -gt 0) {
    $unregistered | ForEach-Object { Write-Error "Unregistered git worktree: $_" }
    if ($FailOnUnregistered) { exit 1 }
}

Write-Host "Worktree reconciliation passed. Unregistered=$($unregistered.Count)"
