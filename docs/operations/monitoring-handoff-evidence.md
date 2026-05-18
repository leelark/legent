# Monitoring Handoff Evidence

Status: template and validator only; live Prometheus or managed metrics evidence is still required.

Use `docs/operations/monitoring-handoff-evidence.template.json` after a release-window monitoring handoff. The completed file must prove the observability Kustomize render, alert rule load, warning route to `platform-primary`, critical route to `platform-pager`, and dashboard or metrics coverage for the release window.

Validate the completed transcript from the release evidence directory:

```powershell
.\scripts\ops\validate-evidence-transcript.ps1 -TranscriptPath .\docs\operations\evidence\<release>\monitoring-handoff-evidence.json -Type monitoring-handoff -MaxAgeDays 14
```

For GA evidence, reference the completed transcript and sanitized supporting files from `ga-evidence-manifest.json` under the `monitoring-alert-routing` artifact. The GA validator still requires `platform-pager` and `platform-primary` evidence terms, current timestamps, pass status, and local files inside the evidence directory. Do not use repo render output alone as live handoff evidence unless target Prometheus or managed metrics rule load and Alertmanager route tests are also attached.
