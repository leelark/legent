# Security Scan Transcript Evidence

Status: template and validator only; real CI or target-environment evidence is still required.

Use `docs/operations/security-scan-transcript.template.json` to wrap the release security scan transcript for GA evidence. The completed file must reference sanitized artifacts for `npm audit`, `gitleaks`, `trivy`, and the filesystem SBOM. It must not contain raw tokens, cookies, private keys, credentials, `.env` values, customer data, or unredacted scanner findings that reveal secrets.

Validate the completed transcript from the release evidence directory:

```powershell
.\scripts\ops\validate-evidence-transcript.ps1 -TranscriptPath .\docs\operations\evidence\<release>\security-scan-transcript.json -Type security-scan -MaxAgeDays 14
```

For GA evidence, map the same completed transcript into `ga-evidence-manifest.json` entries for `ci-gitleaks`, `ci-trivy`, and `ci-sbom`. The GA validator still requires current timestamps, passing statuses, local referenced files inside the evidence directory, and required scanner terms. Do not fabricate CI URLs or SBOM artifacts; use the actual CI run, exported transcript, or release artifact.
