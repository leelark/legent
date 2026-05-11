# Testing And QA Guide

## Backend Tests

```powershell
.\mvnw.cmd test
.\mvnw.cmd -pl services/campaign-service test
.\mvnw.cmd -pl services/audience-service test
```

## Frontend Tests

```powershell
cd frontend
npm run test:e2e -- --project=chromium
```

## Existing Playwright Specs

| Spec | Covered Tests |
| --- | --- |
| frontend/tests/e2e/admin.spec.ts | admin console shows operations, users, and role engine<br>settings console persists preferences and supports deliverability<br>admin console keeps mobile navigation usable |
| frontend/tests/e2e/auth.spec.ts | login page links to recovery and signup |
| frontend/tests/e2e/campaign-engine.spec.ts | campaign wizard saves experiment, budget, and frequency policy<br>campaign tracking exposes safety, DLQ, budget, and variant analytics tabs<br>launch command center scans readiness and executes launch action |
| frontend/tests/e2e/context-bootstrap.spec.ts | login bootstraps workspace context and opens app route |
| frontend/tests/e2e/marketing.spec.ts | premium public navigation, theme persistence, and mobile menu<br>homepage scenarios and pricing yearly toggle work<br>public pages are differentiated and contact submit succeeds<br>auth and onboarding public surfaces preserve required fields |

## Smoke Test

```powershell
python simulate_e2e_flow.py
```

The smoke script signs up a test user, creates subscriber/list/campaign, triggers send, validates automation graph flow, and checks gateway contracts.

## QA Checklist

- Auth session and workspace context bootstrap.
- Tenant/workspace headers on protected API calls.
- Frontend public navigation and mobile menu.
- Campaign wizard draft save and launch readiness.
- Admin settings, role engine, and deliverability screens.
- Kafka event consumers and idempotency paths.
- Delivery retries, replay queue, and provider health.
