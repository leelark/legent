$ErrorActionPreference='Stop'
$base='http://localhost:8080'
$tenant='01HTENANT000000000000000001'
$workspace='workspace-default'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginBody = @{ email='admin@legent.com'; password='Admin@123' } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method POST -WebSession $session -ContentType 'application/json' -Headers @{ 'X-Tenant-Id'=$tenant; 'X-Workspace-Id'=$workspace } -Body $loginBody
Write-Output "LOGIN_OK=$($login.success)"
$headers = @{ 'X-Tenant-Id'=$tenant; 'X-Workspace-Id'=$workspace }
$createBody = @{
  name='Codex Campaign Smoke Final'
  subject='Smoke Subject'
  preheader='Smoke'
  senderName='Admin'
  senderEmail='admin@legent.com'
  trackingEnabled=$true
  complianceEnabled=$true
  approvalRequired=$false
  type='STANDARD'
  audiences=@()
} | ConvertTo-Json -Depth 10
$create = Invoke-RestMethod -Uri "$base/api/v1/campaigns" -Method POST -WebSession $session -Headers $headers -ContentType 'application/json' -Body $createBody
$campaignId = $create.data.id
Write-Output "CAMPAIGN_ID=$campaignId"
$sendBody = @{ triggerSource='MANUAL'; idempotencyKey="smoke-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())" } | ConvertTo-Json
$send = Invoke-RestMethod -Uri "$base/api/v1/campaigns/$campaignId/send" -Method POST -WebSession $session -Headers $headers -ContentType 'application/json' -Body $sendBody
$jobId = $send.data.id
Write-Output "JOB_ID=$jobId"
Start-Sleep -Seconds 2
$job = Invoke-RestMethod -Uri "$base/api/v1/send-jobs/$jobId" -Method GET -WebSession $session -Headers $headers
Write-Output "JOB_STATUS=$($job.data.status)"
$jobs = Invoke-RestMethod -Uri "$base/api/v1/campaigns/$campaignId/jobs?page=0&size=20" -Method GET -WebSession $session -Headers $headers
Write-Output "JOBS_TOTAL=$($jobs.meta.totalElements)"
