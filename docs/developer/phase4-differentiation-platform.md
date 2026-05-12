# Phase 4 Differentiation Platform

## Capabilities

- AI copilot recommendations are deterministic, policy-aware, and stored with human approval state.
- Predictive deliverability scores domain/auth/reputation/blocklist/warmup risk and returns remediation guidance.
- Real-time decisioning evaluates profile updates against active policies, variants, and channel guardrails.
- Experiment analysis adds confidence, lift, auto-winner guardrails, and causal reporting.
- Omnichannel orchestration simulates email, SMS, push, ads, web, and transactional route order.
- Developer platform stores app packages, scopes, SDK targets, sandboxes, marketplace state, and webhook replay jobs.
- SLO automation evaluates error budget burn, latency, saturation, queue depth, and self-healing actions.

## Primary APIs

- `POST /api/v1/differentiation/copilot/recommendations`
- `POST /api/v1/differentiation/copilot/recommendations/{id}/decision`
- `POST /api/v1/differentiation/decisioning/policies`
- `POST /api/v1/differentiation/decisioning/evaluate`
- `POST /api/v1/differentiation/omnichannel/flows`
- `POST /api/v1/differentiation/omnichannel/simulate`
- `POST /api/v1/differentiation/developer/packages`
- `POST /api/v1/differentiation/developer/sandboxes`
- `POST /api/v1/differentiation/developer/webhook-replays`
- `POST /api/v1/differentiation/ops/slo-policies`
- `POST /api/v1/differentiation/ops/slo-policies/evaluate`
- `GET /api/v1/deliverability/predictive-risk`
- `GET /api/v1/campaigns/{id}/experiments/{experimentId}/analysis`

## CLI

```powershell
.\scripts\dev\legent-dev.ps1 -Command create-package -TenantId tenant-1 -WorkspaceId workspace-1
.\scripts\dev\legent-dev.ps1 -Command create-sandbox -AppPackageId pkg-id -TenantId tenant-1
.\scripts\dev\legent-dev.ps1 -Command replay-webhook -AppPackageId pkg-id -DryRun
.\scripts\dev\legent-dev.ps1 -Command seed-slo -ServiceName delivery-service
```

Set `LEGENT_API_TOKEN`, `LEGENT_TENANT_ID`, and `LEGENT_WORKSPACE_ID` for repeat use.

## SDK

TypeScript SDK lives in `sdk/typescript`.

```ts
import { LegentClient } from '@legent/sdk';

const client = new LegentClient({
  apiBaseUrl: 'https://api.example.com',
  token: process.env.LEGENT_API_TOKEN,
  tenantId: 'tenant-1',
  workspaceId: 'workspace-1',
});

await client.evaluateDecisionPolicy({
  policyKey: 'next-best-offer',
  channel: 'EMAIL',
  profileUpdates: { subjectId: 'sub-1', interest: 'upgrade', consent: 'OPT_IN' },
});
```

## Release Notes

These are Phase 4 platform primitives, not autonomous AI generation or external mailbox-provider integrations. Production rollout still needs model provider integration, real ISP/blocklist feeds, push/SMS/ad network connectors, marketplace publishing flow, and live SLO alert routing.
