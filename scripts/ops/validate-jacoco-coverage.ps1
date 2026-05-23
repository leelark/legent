param(
    [double]$MinInstructionPercent = 1.0,
    [double]$MinLinePercent = 1.0,
    [double]$MinBranchPercent = 0.0,
    [double]$MinMethodPercent = 1.0,
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-CoveragePercent($Counter) {
    if ($null -eq $Counter) {
        return 100.0
    }

    $covered = [double]$Counter.covered
    $missed = [double]$Counter.missed
    $total = $covered + $missed
    if ($total -eq 0) {
        return 100.0
    }

    return ($covered / $total) * 100.0
}

function Add-Counter($Totals, [string]$Type, $Counter) {
    if ($null -eq $Counter) {
        return
    }

    $Totals[$Type].Covered += [double]$Counter.covered
    $Totals[$Type].Missed += [double]$Counter.missed
}

function Get-TotalPercent($Totals, [string]$Type) {
    $covered = [double]$Totals[$Type].Covered
    $missed = [double]$Totals[$Type].Missed
    $total = $covered + $missed
    if ($total -eq 0) {
        return 100.0
    }

    return ($covered / $total) * 100.0
}

function Get-ReportCounter($XmlDocument, [string]$Type) {
    return $XmlDocument.SelectSingleNode("/report/counter[@type='$Type']")
}

$searchRoots = @(
    (Join-Path $Root "services"),
    (Join-Path $Root "shared")
) | Where-Object { Test-Path -LiteralPath $_ }

$reports = @(
    Get-ChildItem -Path $searchRoots -Recurse -Filter jacoco.xml -File -ErrorAction SilentlyContinue |
        Where-Object {
            $normalized = $_.FullName.Replace("/", "\")
            $normalized.EndsWith("\target\site\jacoco\jacoco.xml", [System.StringComparison]::OrdinalIgnoreCase)
        }
)

if ($reports.Count -eq 0) {
    Write-Error "No JaCoCo reports found. Run '.\mvnw.cmd -Pcoverage -DskipITs=true verify' first."
    exit 1
}

$totals = @{
    INSTRUCTION = [pscustomobject]@{ Covered = 0.0; Missed = 0.0 }
    LINE = [pscustomobject]@{ Covered = 0.0; Missed = 0.0 }
    BRANCH = [pscustomobject]@{ Covered = 0.0; Missed = 0.0 }
    METHOD = [pscustomobject]@{ Covered = 0.0; Missed = 0.0 }
}

$rows = foreach ($report in $reports) {
    [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw

    $instruction = Get-ReportCounter $xml "INSTRUCTION"
    $line = Get-ReportCounter $xml "LINE"
    $branch = Get-ReportCounter $xml "BRANCH"
    $method = Get-ReportCounter $xml "METHOD"

    Add-Counter $totals "INSTRUCTION" $instruction
    Add-Counter $totals "LINE" $line
    Add-Counter $totals "BRANCH" $branch
    Add-Counter $totals "METHOD" $method

    [pscustomobject]@{
        Report = $report.FullName.Replace($Root, "").TrimStart([char[]]"\/")
        Instruction = "{0:N2}%" -f (Get-CoveragePercent $instruction)
        Line = "{0:N2}%" -f (Get-CoveragePercent $line)
        Branch = "{0:N2}%" -f (Get-CoveragePercent $branch)
        Method = "{0:N2}%" -f (Get-CoveragePercent $method)
    }
}

Write-Host "JaCoCo report count: $($reports.Count)"
$rows | Sort-Object Report | Format-Table -AutoSize

$summary = [ordered]@{
    Instruction = Get-TotalPercent $totals "INSTRUCTION"
    Line = Get-TotalPercent $totals "LINE"
    Branch = Get-TotalPercent $totals "BRANCH"
    Method = Get-TotalPercent $totals "METHOD"
}

$thresholds = [ordered]@{
    Instruction = $MinInstructionPercent
    Line = $MinLinePercent
    Branch = $MinBranchPercent
    Method = $MinMethodPercent
}

$failed = @()
foreach ($key in $summary.Keys) {
    Write-Host ("Aggregate {0}: {1:N2}% (minimum {2:N2}%)" -f $key, $summary[$key], $thresholds[$key])
    if ($summary[$key] -lt $thresholds[$key]) {
        $failed += $key
    }
}

if ($failed.Count -gt 0) {
    Write-Error "JaCoCo aggregate coverage below threshold for: $($failed -join ', ')."
    exit 1
}

Write-Host "JaCoCo aggregate coverage thresholds passed."
