param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [ValidateSet("Auto", "Csv", "Json")]
    [string] $Format = "Auto",

    [string] $ReviewedBy,
    [string] $ReviewedAt,
    [string] $ReviewTicket,

    [switch] $EmitSql,
    [string] $SqlOutputPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-InputFile([string] $Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "InputPath is required."
    }

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Input file not found: $Path"
    }

    return (Resolve-Path -LiteralPath $Path).Path
}

function Resolve-MappingFormat([string] $Path, [string] $RequestedFormat) {
    if ($RequestedFormat -ne "Auto") {
        return $RequestedFormat
    }

    $extension = [System.IO.Path]::GetExtension($Path).ToLowerInvariant()
    switch ($extension) {
        ".csv" { return "Csv" }
        ".json" { return "Json" }
        default {
            throw "Cannot infer mapping export format from extension '$extension'. Use -Format Csv or -Format Json."
        }
    }
}

function Read-StrictCsv([string] $Path) {
    try {
        Add-Type -AssemblyName Microsoft.VisualBasic -ErrorAction Stop
    }
    catch {
        throw "CSV parser unavailable: $($_.Exception.Message)"
    }

    $parser = [Microsoft.VisualBasic.FileIO.TextFieldParser]::new($Path)
    $parser.TextFieldType = [Microsoft.VisualBasic.FileIO.FieldType]::Delimited
    $parser.SetDelimiters(",")
    $parser.HasFieldsEnclosedInQuotes = $true
    $parser.TrimWhiteSpace = $false

    try {
        if ($parser.EndOfData) {
            throw "Malformed CSV: file is empty."
        }

        $rawHeaders = $parser.ReadFields()
        if ($null -eq $rawHeaders -or $rawHeaders.Count -eq 0) {
            throw "Malformed CSV: header row is empty."
        }

        $headers = @()
        $seenHeaders = @{}
        foreach ($rawHeader in $rawHeaders) {
            $header = ([string] $rawHeader).Trim()
            if ([string]::IsNullOrWhiteSpace($header)) {
                throw "Malformed CSV: header row contains an empty column name."
            }
            $headerKey = $header.ToLowerInvariant()
            if ($seenHeaders.ContainsKey($headerKey)) {
                throw "Malformed CSV: duplicate header '$header'."
            }
            $seenHeaders[$headerKey] = $true
            $headers += $header
        }

        $rows = New-Object System.Collections.Generic.List[object]
        while (-not $parser.EndOfData) {
            $lineNumber = $parser.LineNumber
            $fields = $parser.ReadFields()
            if ($null -eq $fields) {
                continue
            }
            if ($fields.Count -ne $headers.Count) {
                throw "Malformed CSV at line $lineNumber`: expected $($headers.Count) fields, found $($fields.Count)."
            }

            $row = [ordered]@{}
            for ($i = 0; $i -lt $headers.Count; $i++) {
                $row[$headers[$i]] = $fields[$i]
            }
            $rows.Add([pscustomobject] $row)
        }

        return $rows.ToArray()
    }
    catch [Microsoft.VisualBasic.FileIO.MalformedLineException] {
        throw "Malformed CSV at line $($_.Exception.LineNumber): $($_.Exception.Message)"
    }
    finally {
        $parser.Close()
    }
}

function Read-StrictJson([string] $Path) {
    try {
        $parsed = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        throw "Malformed JSON: $($_.Exception.Message)"
    }

    if ($null -eq $parsed) {
        throw "Malformed JSON: file did not contain a JSON object or array."
    }

    if ($parsed -is [System.Array]) {
        return @($parsed)
    }

    $mappingsProperty = $parsed.PSObject.Properties | Where-Object { $_.Name -ieq "mappings" } | Select-Object -First 1
    if ($null -ne $mappingsProperty) {
        return @($mappingsProperty.Value)
    }

    $rowsProperty = $parsed.PSObject.Properties | Where-Object { $_.Name -ieq "rows" } | Select-Object -First 1
    if ($null -ne $rowsProperty) {
        return @($rowsProperty.Value)
    }

    $tenantProperty = $parsed.PSObject.Properties | Where-Object { $_.Name -ieq "tenant_id" } | Select-Object -First 1
    if ($null -ne $tenantProperty) {
        return @($parsed)
    }

    throw "Malformed JSON: expected an array, an object with mappings or rows, or a single mapping object."
}

function Get-FieldValue([object] $Row, [string] $Name) {
    if ($null -eq $Row) {
        return $null
    }

    $property = $Row.PSObject.Properties | Where-Object { $_.Name -ieq $Name } | Select-Object -First 1
    if ($null -eq $property -or $null -eq $property.Value) {
        return $null
    }

    return [string] $property.Value
}

function Test-Blank([AllowNull()] [string] $Value) {
    return [string]::IsNullOrWhiteSpace($Value)
}

function Test-PlaceholderWorkspaceId([string] $Value) {
    $normalized = $Value.Trim().ToLowerInvariant()
    $exactPlaceholders = @(
        "workspace-default",
        "workspace-id",
        "workspace_id",
        "target-workspace-id",
        "target_workspace_id",
        "your-workspace-id",
        "your_workspace_id",
        "replace_with_workspace_id",
        "replace-with-workspace-id",
        "change_me",
        "changeme",
        "todo",
        "tbd",
        "n/a",
        "na",
        "none",
        "null",
        "<workspace-id>",
        "<target-workspace-id>",
        "{workspace-id}",
        "{target-workspace-id}",
        "00000000-0000-0000-0000-000000000000"
    )

    if ($exactPlaceholders -contains $normalized) {
        return $true
    }

    return $normalized -match "^(replace[_-]with|placeholder|sample|example)[_-]?.*workspace"
}

function ConvertTo-ReviewedAtValue([string] $Value, [int] $RowNumber, [System.Collections.Generic.List[string]] $Errors) {
    $parsed = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParse($Value, [ref] $parsed)) {
        $Errors.Add("row $RowNumber reviewed_at must be an ISO-8601 timestamp or another PowerShell/.NET parseable timestamp.")
        return $null
    }

    return $parsed.ToUniversalTime().ToString("o")
}

function Assert-NoOuterWhitespace([string] $ColumnName, [string] $Value, [int] $RowNumber, [System.Collections.Generic.List[string]] $Errors) {
    if ($Value -ne $Value.Trim()) {
        $Errors.Add("row $RowNumber $ColumnName must not include leading or trailing whitespace.")
    }
}

function ConvertTo-MappingRows([object[]] $Rows) {
    if ($Rows.Count -eq 0) {
        throw "Audience data-extension workspace mapping export contains no rows."
    }

    $errors = New-Object System.Collections.Generic.List[string]
    $normalizedRows = New-Object System.Collections.Generic.List[object]
    $seenDataExtensionIds = @{}

    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $rowNumber = $i + 1
        $row = $Rows[$i]

        $tenantId = Get-FieldValue $row "tenant_id"
        $dataExtensionId = Get-FieldValue $row "data_extension_id"
        $targetWorkspaceId = Get-FieldValue $row "target_workspace_id"
        $rowReviewedBy = Get-FieldValue $row "reviewed_by"
        $rowReviewedAt = Get-FieldValue $row "reviewed_at"
        $rowReviewTicket = Get-FieldValue $row "review_ticket"

        if (Test-Blank $rowReviewedBy) {
            $rowReviewedBy = $ReviewedBy
        }
        if (Test-Blank $rowReviewedAt) {
            $rowReviewedAt = $ReviewedAt
        }
        if (Test-Blank $rowReviewTicket -and -not [string]::IsNullOrWhiteSpace($ReviewTicket)) {
            $rowReviewTicket = $ReviewTicket
        }

        if (Test-Blank $tenantId) {
            $errors.Add("row $rowNumber tenant_id is required.")
        }
        else {
            Assert-NoOuterWhitespace "tenant_id" $tenantId $rowNumber $errors
        }

        if (Test-Blank $dataExtensionId) {
            $errors.Add("row $rowNumber data_extension_id is required.")
        }
        else {
            Assert-NoOuterWhitespace "data_extension_id" $dataExtensionId $rowNumber $errors
            $dataExtensionKey = $dataExtensionId.Trim().ToLowerInvariant()
            if ($seenDataExtensionIds.ContainsKey($dataExtensionKey)) {
                $errors.Add("duplicate data_extension_id '$($dataExtensionId.Trim())' appears on rows $($seenDataExtensionIds[$dataExtensionKey]) and $rowNumber.")
            }
            else {
                $seenDataExtensionIds[$dataExtensionKey] = $rowNumber
            }
        }

        if (Test-Blank $targetWorkspaceId) {
            $errors.Add("row $rowNumber target_workspace_id is required.")
        }
        else {
            Assert-NoOuterWhitespace "target_workspace_id" $targetWorkspaceId $rowNumber $errors
            if (Test-PlaceholderWorkspaceId $targetWorkspaceId) {
                $errors.Add("row $rowNumber target_workspace_id '$($targetWorkspaceId.Trim())' is a placeholder and must be replaced with the reviewed workspace ID.")
            }
        }

        if (Test-Blank $rowReviewedBy) {
            $errors.Add("row $rowNumber reviewed_by is required; provide a reviewed_by column or -ReviewedBy.")
        }
        else {
            Assert-NoOuterWhitespace "reviewed_by" $rowReviewedBy $rowNumber $errors
        }

        $reviewedAtValue = $null
        if (Test-Blank $rowReviewedAt) {
            $errors.Add("row $rowNumber reviewed_at is required; provide a reviewed_at column or -ReviewedAt.")
        }
        else {
            Assert-NoOuterWhitespace "reviewed_at" $rowReviewedAt $rowNumber $errors
            $reviewedAtValue = ConvertTo-ReviewedAtValue $rowReviewedAt $rowNumber $errors
        }

        if ($errors.Count -eq 0) {
            $normalizedRows.Add([pscustomobject]@{
                    TenantId          = $tenantId.Trim()
                    DataExtensionId   = $dataExtensionId.Trim()
                    TargetWorkspaceId = $targetWorkspaceId.Trim()
                    ReviewedBy        = $rowReviewedBy.Trim()
                    ReviewedAt        = $reviewedAtValue
                    ReviewTicket      = if (Test-Blank $rowReviewTicket) { $null } else { $rowReviewTicket.Trim() }
                })
        }
    }

    if ($errors.Count -gt 0) {
        throw "Audience data-extension workspace mapping validation failed:`n - $($errors -join "`n - ")"
    }

    return $normalizedRows.ToArray()
}

function ConvertTo-SqlLiteral([AllowNull()] [string] $Value) {
    if ($null -eq $Value) {
        return "NULL"
    }

    return "'$($Value.Replace("'", "''"))'"
}

function New-MappingReviewSql([object[]] $Rows) {
    $lines = New-Object System.Collections.Generic.List[string]
    $generatedAt = (Get-Date).ToUniversalTime().ToString("o")

    $lines.Add("-- Generated by scripts/ops/validate-audience-data-extension-workspace-mapping.ps1 at $generatedAt UTC.")
    $lines.Add("-- Review target_workspace_id values against the authoritative foundation workspace source before applying.")
    $lines.Add("BEGIN;")
    $lines.Add("")
    $lines.Add("CREATE TABLE IF NOT EXISTS public.audience_data_extension_workspace_mapping_review (")
    $lines.Add("    tenant_id VARCHAR(36) NOT NULL,")
    $lines.Add("    data_extension_id VARCHAR(36) NOT NULL,")
    $lines.Add("    target_workspace_id VARCHAR(36) NOT NULL,")
    $lines.Add("    reviewed_by VARCHAR(255) NOT NULL,")
    $lines.Add("    reviewed_at TIMESTAMPTZ NOT NULL,")
    $lines.Add("    review_ticket VARCHAR(255),")
    $lines.Add("    PRIMARY KEY (tenant_id, data_extension_id)")
    $lines.Add(");")
    $lines.Add("")
    $lines.Add("INSERT INTO public.audience_data_extension_workspace_mapping_review (")
    $lines.Add("    tenant_id,")
    $lines.Add("    data_extension_id,")
    $lines.Add("    target_workspace_id,")
    $lines.Add("    reviewed_by,")
    $lines.Add("    reviewed_at,")
    $lines.Add("    review_ticket")
    $lines.Add(")")
    $lines.Add("VALUES")

    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $row = $Rows[$i]
        $suffix = if ($i -eq ($Rows.Count - 1)) { ";" } else { "," }
        $lines.Add("    ($(ConvertTo-SqlLiteral $row.TenantId), $(ConvertTo-SqlLiteral $row.DataExtensionId), $(ConvertTo-SqlLiteral $row.TargetWorkspaceId), $(ConvertTo-SqlLiteral $row.ReviewedBy), $(ConvertTo-SqlLiteral $row.ReviewedAt)::timestamptz, $(ConvertTo-SqlLiteral $row.ReviewTicket))$suffix")
    }

    $lines.Add("")
    $lines.Add("COMMIT;")

    return $lines -join [Environment]::NewLine
}

function Write-SqlOutput([string] $Sql, [string] $Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return
    }

    $resolvedPath = if ([System.IO.Path]::IsPathRooted($Path)) {
        $Path
    }
    else {
        Join-Path (Get-Location) $Path
    }

    $parent = Split-Path -Parent $resolvedPath
    if (-not [string]::IsNullOrWhiteSpace($parent) -and -not (Test-Path -LiteralPath $parent -PathType Container)) {
        throw "SQL output directory does not exist: $parent"
    }

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($resolvedPath, $Sql + [Environment]::NewLine, $utf8NoBom)
}

$resolvedInputPath = Resolve-InputFile $InputPath
$mappingFormat = Resolve-MappingFormat $resolvedInputPath $Format

$rawRows = @(switch ($mappingFormat) {
    "Csv" { Read-StrictCsv $resolvedInputPath }
    "Json" { Read-StrictJson $resolvedInputPath }
})

$mappingRows = @(ConvertTo-MappingRows $rawRows)

if ($EmitSql -or -not [string]::IsNullOrWhiteSpace($SqlOutputPath)) {
    $sql = New-MappingReviewSql $mappingRows
    Write-SqlOutput $sql $SqlOutputPath
    if ($EmitSql) {
        Write-Output $sql
    }
}

Write-Host "Audience data-extension workspace mapping validation passed for $($mappingRows.Count) rows from $mappingFormat export."
