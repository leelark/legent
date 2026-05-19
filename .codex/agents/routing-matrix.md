# Routing Matrix

Use this matrix to choose owners and validation for new work.

| Task type | Primary owner | Required partners | Validation |
|---|---|---|---|
| Requirements or ambiguous feature | REQUIREMENTS_ANALYST | PRODUCT_MANAGER_AGENT, UX_STRATEGIST | Acceptance criteria and non-goals |
| Salesforce/competitor parity | SALESFORCE_PARITY_RESEARCHER | PRODUCT_MANAGER_AGENT, PRINCIPAL_ARCHITECT | Current official-source notes and product gap list |
| Cross-service architecture | PRINCIPAL_ARCHITECT | SYSTEM_DESIGNER, DATA_ARCHITECT, SECURITY_ENGINEER | ADR/design decision plus impacted tests |
| API route change | API_ARCHITECT | BACKEND_SERVICE_OWNER, FRONTEND_OWNER, DEVOPS_ENGINEER | Route map, Nginx, ingress, API/client tests |
| Backend service behavior | BACKEND_SERVICE_OWNER | SECURITY_ENGINEER when auth/tenant; TEST_ARCHITECT | Focused Maven tests |
| Shared module change | SHARED_PLATFORM_OWNER | All impacted service owners | Shared tests plus impacted service tests |
| Frontend route/layout | FRONTEND_OWNER | UX_STRATEGIST, QA_ENGINEER | Lint, build, impacted Playwright/browser checks |
| Database migration | DATABASE_ENGINEER | BACKEND_SERVICE_OWNER, RELEASE_MANAGER | Migration tests, schema validation, rollback notes |
| Kafka/event change | SYSTEM_DESIGNER | BACKEND_SERVICE_OWNER, TEST_ARCHITECT | Publisher/consumer contract tests |
| Security hardening | SECURITY_ENGINEER | Affected owner, RELEASE_MANAGER | Security tests, artifact hygiene, scans when feasible |
| Performance/high-volume path | PERFORMANCE_ENGINEER | SYSTEM_DESIGNER, DATA_ARCHITECT, SRE_MONITORING_ENGINEER | Focused perf validation or explicit residual risk |
| Compose/Kubernetes/CI | DEVOPS_ENGINEER | SRE_MONITORING_ENGINEER, RELEASE_MANAGER | Compose config, Kustomize render, ops validators |
| Observability/incident | SRE_MONITORING_ENGINEER | DEVOPS_ENGINEER, BACKEND_SERVICE_OWNER | Alert/dashboard/runbook checks |
| Test expansion | TEST_ARCHITECT | QA_ENGINEER, affected owner | Targeted test execution |
| Release readiness | RELEASE_MANAGER | SECURITY_ENGINEER, DEVOPS_ENGINEER, QA_ENGINEER | Release gate and evidence matrix |
| Refactor/dead code | REFACTORING_ENGINEER | affected owner, TEST_ARCHITECT | Behavior-preserving tests |
| Bug fix | BUGFIX_ENGINEER | affected owner, QA_ENGINEER | Reproduction and regression test |
| Documentation or memory | DOCUMENTATION_ENGINEER | TECHNICAL_DEBT_STEWARD | Link/path validation and drift scan |

## Priority Formula

Use this score for backlog ordering:

`(ProductionReadinessImpact * 5) + (SecurityRisk * 4) + (UserImpact * 3) + (PerformanceImpact * 2) + TechnicalDebtImpact`

Each dimension is 0 to 5.

## Default Assignment Rules

- If tenant isolation, cookies, auth, SCIM, secrets, signed URLs, sanitization, or public endpoints are touched, add SECURITY_ENGINEER.
- If high-volume send, tracking, imports, webhooks, Kafka, or ClickHouse are touched, add PERFORMANCE_ENGINEER.
- If route ownership changes, add API_ARCHITECT and DEVOPS_ENGINEER.
- If Flyway changes, add DATABASE_ENGINEER and RELEASE_MANAGER.
- If UI changes are visible, add UX_STRATEGIST and QA_ENGINEER.
- If work spans more than two services, add PRINCIPAL_ARCHITECT and PROGRAM_MANAGER_AGENT.
- If release claims are involved, add RELEASE_MANAGER and require evidence.
