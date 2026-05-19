param(
    [string[]]$ChangedPath = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$profiles = New-Object System.Collections.Generic.HashSet[string]
foreach ($path in $ChangedPath) {
    if ($path -like "frontend/*" -or $path -like "frontend\\*") { [void]$profiles.Add("frontend-focused") }
    if ($path -like "services/*" -or $path -like "services\\*" -or $path -like "shared/*" -or $path -like "shared\\*") { [void]$profiles.Add("backend-focused") }
    if ($path -like "config/*" -or $path -like "config\\*") { [void]$profiles.Add("route-runtime") }
    if ($path -like "infrastructure/*" -or $path -like "infrastructure\\*" -or $path -like "docker-compose*") { [void]$profiles.Add("release") }
    if ($path -like ".codex/*" -or $path -like ".codex\\*" -or $path -like "docs/*" -or $path -like "docs\\*") { [void]$profiles.Add("docs-memory") }
    if ($path -like "scripts/*" -or $path -like "scripts\\*") { [void]$profiles.Add("ops-validation") }
}

if ($profiles.Count -eq 0) { [void]$profiles.Add("manual-select") }
$profiles | Sort-Object
