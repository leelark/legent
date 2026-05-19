param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

& .codex/utilities/validate-codex-system.ps1
exit $LASTEXITCODE
