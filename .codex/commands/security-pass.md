# security-pass

Purpose: remove security and compliance blockers.

Commands:

```powershell
rg -n "permitAll|csrf|SameSite|HttpOnly|TenantContext|X-Tenant-Id|trusted\.packages|ddl-auto" services shared frontend config infrastructure -g "!**/target/**" -g "!**/node_modules/**" -g "!**/.next/**"
rg -l "password|secret|token|private key" services shared frontend config infrastructure scripts docs -g "!**/target/**" -g "!**/node_modules/**" -g "!**/.next/**"
cd frontend
npm audit --omit=dev --audit-level=high
```

Rules:

- Do not print `.env` or secret values.
- Narrow Kafka deserialization packages when touching configs.
- Production must use Flyway and `ddl-auto=validate`.
- Preserve cookie auth, CSRF/origin guard, signed tracking URLs, suppressions, unsubscribe, warmup, and rate controls.
- Record findings in `.codex/memory/security-findings.md`.
