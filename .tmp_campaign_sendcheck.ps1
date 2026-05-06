$ErrorActionPreference='Stop'
$base='http://localhost:8080'
$tenant='01HTENANT000000000000000001'
$workspace='workspace-default'
$segmentId='01KQWX30TM5DE5S62SKT5A89ET'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginBody = @{ email='admin@legent.com'; password='Admin@123' } | ConvertTo-Json
Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method POST -WebSession $session -ContentType 'application/json' -Headers @{ 'X-Tenant-Id'=$tenant; 'X-Workspace-Id'=$workspace } -Body $loginBody | Out-Null
$headers = @{ 'X-Tenant-Id'=$tenant; 'X-Workspace-Id'=$workspace }
$createBody = @{
  name='Codex Campaign SendCheck 2'
  subject='Smoke Subject'
  preheader='Smoke'
  senderName='Admin'
  senderEmail='admin@legent.com'
  trackingEnabled=$true
  complianceEnabled=$true
  approvalRequired=$false
  type='STANDARD'
  audiences=@(@{ audienceType='SEGMENT'; audienceId=$segmentId; action='INCLUDE' })
} | ConvertTo-Json -Depth 12
$create = Invoke-RestMethod -Uri "$base/api/v1/campaigns" -Method POST -WebSession $session -Headers $headers -ContentType 'application/json' -Body $createBody
$campaignId = $create.data.id
Write-Output "CID=$campaignId"
$sendBody = @{ triggerSource='MANUAL'; idempotencyKey="smoke-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())" } | ConvertTo-Json
try {
  $send = Invoke-RestMethod -Uri "$base/api/v1/campaigns/$campaignId/send" -Method POST -WebSession $session -Headers $headers -ContentType 'application/json' -Body $sendBody
  Write-Output "SEND_OK=$($send.success)"
  Write-Output "JOB_ID=$($send.data.id)"
} catch {
  $resp = $_.Exception.Response
  Write-Output "SEND_STATUS=$([int]$resp.StatusCode)"
  $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
  $body = $reader.ReadToEnd()
  Write-Output "SEND_BODY=$body"
}
