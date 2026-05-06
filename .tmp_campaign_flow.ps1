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
  name="Codex Campaign Final-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
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
Write-Output "CAMPAIGN_ID=$campaignId"
$sendBody = @{ triggerSource='MANUAL'; idempotencyKey="smoke-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())" } | ConvertTo-Json
$send = Invoke-RestMethod -Uri "$base/api/v1/campaigns/$campaignId/send" -Method POST -WebSession $session -Headers $headers -ContentType 'application/json' -Body $sendBody
$jobId = $send.data.id
Write-Output "JOB_ID=$jobId"
for ($i=0; $i -lt 6; $i++) {
  Start-Sleep -Seconds 2
  try {
    $job = Invoke-RestMethod -Uri "$base/api/v1/send-jobs/$jobId" -Method GET -WebSession $session -Headers $headers
    Write-Output "POLL_$i=$($job.data.status):target=$($job.data.totalTarget),sent=$($job.data.sentCount),failed=$($job.data.failedCount)"
  } catch {
    $resp = $_.Exception.Response
    if ($resp -ne $null) {
      $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
      $body = $reader.ReadToEnd()
      Write-Output "POLL_$i=ERR_$([int]$resp.StatusCode):$body"
    } else {
      Write-Output "POLL_$i=ERR:$($_.Exception.Message)"
    }
  }
}
