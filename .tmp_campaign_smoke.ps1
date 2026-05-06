Continue='Continue'
='http://localhost:8080'
='01HTENANT000000000000000001'
='workspace-default'
 = New-Object Microsoft.PowerShell.Commands.WebRequestSession
 = @{ email='admin@legent.com'; password='Admin@123' } | ConvertTo-Json
 = Invoke-WebRequest -UseBasicParsing -Uri "/api/v1/auth/login" -Method POST -WebSession  -ContentType 'application/json' -Headers @{ 'X-Tenant-Id'=; 'X-Workspace-Id'= } -Body 
Write-Output "LOGIN="
 = @{ 'X-Tenant-Id'=; 'X-Workspace-Id'=; 'Content-Type'='application/json' }
 = [ordered]@{
  name='Codex Campaign Smoke 4'
  subject='Smoke Subject'
  preheader='Smoke'
  senderName='Admin'
  senderEmail='admin@legent.com'
  trackingEnabled=True
  complianceEnabled=True
  approvalRequired=False
  type='STANDARD'
  audiences=@()
}
 =  | ConvertTo-Json -Depth 8
Write-Output "BODY="
try {
   = Invoke-WebRequest -UseBasicParsing -Uri "/api/v1/campaigns" -Method POST -WebSession  -Headers  -Body 
  Write-Output "CREATE_STATUS="
  Write-Output "CREATE_BODY="
} catch {
  Write-Output "ERR_CAUGHT"
   = .Exception.Response
  if ( -ne ) {
     = [int].StatusCode
    Write-Output "CREATE_STATUS="
     = New-Object System.IO.StreamReader(.GetResponseStream())
     = .ReadToEnd()
    Write-Output "CREATE_BODY="
  } else {
    Write-Output "CREATE_ERROR="
  }
}
